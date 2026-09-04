/*
 * Copyright 2026 Michael Schout
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.project.TimeEntryRepository
import io.github.mschout.gitlab.toggltimer.project.TogglSyncService
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.toggl.UpdateTimeEntryStartRequest
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

private val startUpdateLogger = KotlinLogging.logger {}

data class UpdateTimeEntryStartCommand(
    val togglId: Long,
    val expectedStart: Instant,
    val startDate: LocalDate,
    val startTime: String,
)

sealed interface TimeEntryStartUpdateOutcome {
  data class Saved(val entry: TogglTimeEntry, val historySynchronized: Boolean) :
      TimeEntryStartUpdateOutcome

  data class Unchanged(val entry: TogglTimeEntry) : TimeEntryStartUpdateOutcome

  data class Stale(val currentEntry: TogglTimeEntry?) : TimeEntryStartUpdateOutcome
}

class TimeEntryStartValidationException(message: String) : IllegalArgumentException(message)

class TogglStartUpdateException(cause: Throwable) :
    RuntimeException("Toggl rejected the start time update", cause)

@Service
class TimeEntryStartService(
    private val timeEntryRepository: TimeEntryRepository,
    private val togglClientFactory: TogglClientFactory,
    private val credentialsService: CurrentUserCredentialsService,
    private val togglSyncService: TogglSyncService,
    private val clock: Clock,
) {

  fun updateStart(command: UpdateTimeEntryStartCommand): TimeEntryStartUpdateOutcome {
    val userId = credentialsService.currentUserId()
    val localEntry =
        timeEntryRepository.findByTogglIdAndUserId(command.togglId, userId)
            ?: throw TimeEntryNotFoundException(command.togglId)
    val zone = credentialsService.currentTimeZone()
    val localTime = parseTime(command.startTime)
    val originalStart = command.expectedStart.atZone(zone)
    val visibleStartUnchanged =
        command.startDate == originalStart.toLocalDate() &&
            localTime == originalStart.toLocalTime().truncatedTo(ChronoUnit.MINUTES)

    val requestedStart =
        if (visibleStartUnchanged) {
          command.expectedStart
        } else {
          val minuteStart = resolveLocalMinute(command.startDate, localTime, zone)
          val previousStop =
              timeEntryRepository
                  .findCompletedEndingInRange(
                      userId = userId,
                      startInclusive = minuteStart,
                      endExclusive = minuteStart.plusSeconds(60),
                      pageable = PageRequest.of(0, 1),
                  )
                  .firstOrNull()
                  ?.stop
          previousStop ?: minuteStart
        }
    if (requestedStart.isAfter(clock.instant())) {
      throw TimeEntryStartValidationException("Start time cannot be in the future.")
    }

    val client = togglClientFactory.forApiKey(credentialsService.requireTogglApiKey())
    val current =
        try {
          client.getCurrentTimeEntry()
        } catch (exception: Exception) {
          throw TogglStartUpdateException(exception)
        }
    if (
        localEntry.start != command.expectedStart ||
            current == null ||
            current.id != command.togglId ||
            current.start != command.expectedStart ||
            current.workspaceId != localEntry.workspaceId ||
            current.duration >= 0
    ) {
      return TimeEntryStartUpdateOutcome.Stale(current)
    }
    if (visibleStartUnchanged) return TimeEntryStartUpdateOutcome.Unchanged(current)

    val updated =
        try {
          client.updateTimeEntryStart(
              workspaceId = localEntry.workspaceId,
              timeEntryId = command.togglId,
              request =
                  UpdateTimeEntryStartRequest(
                      workspaceId = localEntry.workspaceId,
                      start = requestedStart,
                  ),
          )
        } catch (exception: Exception) {
          throw TogglStartUpdateException(exception)
        }
    val historySynchronized =
        runCatching { togglSyncService.upsertTimeEntry(userId, updated) }
            .onFailure {
              startUpdateLogger.warn(it) {
                "Failed to sync updated start for Toggl time entry ${command.togglId}"
              }
            }
            .isSuccess
    return TimeEntryStartUpdateOutcome.Saved(
        entry = updated,
        historySynchronized = historySynchronized,
    )
  }

  private fun parseTime(value: String): LocalTime {
    val match =
        TIME_PATTERN.matchEntire(value.trim())
            ?: throw TimeEntryStartValidationException("Use a time such as 6:02 PM.")
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    val isPm = match.groupValues[3].equals("PM", ignoreCase = true)
    val hourOfDay = (hour % 12) + if (isPm) 12 else 0
    return LocalTime.of(hourOfDay, minute)
  }

  private fun resolveLocalMinute(date: LocalDate, time: LocalTime, zone: ZoneId): Instant {
    val localDateTime = LocalDateTime.of(date, time)
    val offsets = zone.rules.getValidOffsets(localDateTime)
    if (offsets.isEmpty()) {
      throw TimeEntryStartValidationException(
          "That local time does not exist because of daylight saving time."
      )
    }
    return localDateTime.toInstant(offsets.first())
  }

  companion object {
    private val TIME_PATTERN = Regex("^(1[0-2]|0?[1-9]):([0-5]\\d)\\s*([AaPp][Mm])$")
  }
}
