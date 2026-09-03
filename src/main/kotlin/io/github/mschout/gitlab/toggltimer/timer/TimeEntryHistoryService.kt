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

import io.github.mschout.gitlab.toggltimer.project.ClientRepository
import io.github.mschout.gitlab.toggltimer.project.Project
import io.github.mschout.gitlab.toggltimer.project.ProjectRepository
import io.github.mschout.gitlab.toggltimer.project.TimeEntry
import io.github.mschout.gitlab.toggltimer.project.TimeEntryRepository
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class TimeEntryHistoryPage(
    val groups: List<TimeEntryDayGroup>,
    val rangeLabel: String,
    val nextBefore: LocalDate?,
    val initial: Boolean,
)

data class TimeEntryDayGroup(
    val label: String,
    val totalFormatted: String,
    val entries: List<RecentTimeEntryView>,
)

data class TimeEntryTotalsView(
    val todayCompletedSeconds: Long,
    val todayCompletedFormatted: String,
    val weekCompletedSeconds: Long,
    val weekCompletedFormatted: String,
    val todayStart: Instant,
    val weekStart: Instant,
    val endExclusive: Instant,
)

data class RecentTimeEntryView(
    val descriptionEditor: TimeEntryDescriptionEditorView,
    val projectPicker: TimeEntryProjectPickerView,
    val actions: TimeEntryActionsView,
    val timeRange: String,
    val durationFormatted: String,
)

data class TimeEntryActionsView(
    val togglId: Long,
    val description: String?,
    val error: String? = null,
    val open: Boolean = false,
    val split: TimeEntrySplitView? = null,
)

data class TimeEntrySplitView(
    val togglId: Long,
    val expectedStart: Instant,
    val expectedStop: Instant,
    val durationSeconds: Long,
    val splitOffsetSeconds: Long,
    val timeZone: String,
    val startEpochMilliseconds: Long,
    val startLocalSecondOfDay: Int,
    val startOffsetSeconds: Int,
    val stopOffsetSeconds: Int,
    val error: String? = null,
    val open: Boolean = false,
)

@Service
@Transactional(readOnly = true)
class TimeEntryHistoryService(
    private val timeEntryRepository: TimeEntryRepository,
    private val projectRepository: ProjectRepository,
    private val clientRepository: ClientRepository,
    private val credentialsService: CurrentUserCredentialsService,
    private val clock: Clock,
) {

  fun initialPage(): TimeEntryHistoryPage {
    val zone = credentialsService.currentTimeZone()
    val today = LocalDate.now(clock.withZone(zone))
    return loadPage(
        startDate = today.minusDays(DAYS_PER_PAGE - 1L),
        endDateExclusive = today.plusDays(1),
        today = today,
        zone = zone,
        initial = true,
    )
  }

  fun currentTotals(): TimeEntryTotalsView {
    val zone = credentialsService.currentTimeZone()
    val today = LocalDate.now(clock.withZone(zone))
    val todayStart = today.atStartOfDay(zone).toInstant()
    val weekStart =
        today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone)
            .toInstant()
    val endExclusive = today.plusDays(1).atStartOfDay(zone).toInstant()
    val entries =
        timeEntryRepository.findCompletedInRange(
            userId = credentialsService.currentUserId(),
            startInclusive = weekStart,
            endExclusive = endExclusive,
        )
    val weekCompletedSeconds = entries.sumOf { it.duration }
    val todayCompletedSeconds =
        entries.filter { !it.start.isBefore(todayStart) }.sumOf { it.duration }

    return TimeEntryTotalsView(
        todayCompletedSeconds = todayCompletedSeconds,
        todayCompletedFormatted = formatDuration(todayCompletedSeconds),
        weekCompletedSeconds = weekCompletedSeconds,
        weekCompletedFormatted = formatDuration(weekCompletedSeconds),
        todayStart = todayStart,
        weekStart = weekStart,
        endExclusive = endExclusive,
    )
  }

  fun pageBefore(before: LocalDate): TimeEntryHistoryPage {
    val zone = credentialsService.currentTimeZone()
    val today = LocalDate.now(clock.withZone(zone))
    return loadPage(
        startDate = before.minusDays(DAYS_PER_PAGE),
        endDateExclusive = before,
        today = today,
        zone = zone,
        initial = false,
    )
  }

  private fun loadPage(
      startDate: LocalDate,
      endDateExclusive: LocalDate,
      today: LocalDate,
      zone: ZoneId,
      initial: Boolean,
  ): TimeEntryHistoryPage {
    val userId = credentialsService.currentUserId()
    val startInclusive = startDate.atStartOfDay(zone).toInstant()
    val endExclusive = endDateExclusive.atStartOfDay(zone).toInstant()
    val entries =
        timeEntryRepository.findCompletedInRange(
            userId = userId,
            startInclusive = startInclusive,
            endExclusive = endExclusive,
        )
    val projectsByTogglId = loadProjects(entries)
    val clientsByTogglId = loadClients(projectsByTogglId.values)
    val groups =
        entries
            .groupBy { it.start.atZone(zone).toLocalDate() }
            .map { (date, dayEntries) ->
              TimeEntryDayGroup(
                  label = formatDayLabel(date = date, today = today),
                  totalFormatted = formatDuration(dayEntries.sumOf { it.duration }),
                  entries =
                      dayEntries.map { entry ->
                        val project = entry.projectId?.let(projectsByTogglId::get)
                        val client = project?.togglClientId?.let(clientsByTogglId::get)
                        entry.toView(project = project, clientName = client?.name, zone = zone)
                      },
              )
            }

    return TimeEntryHistoryPage(
        groups = groups,
        rangeLabel = formatRange(startDate, endDateExclusive.minusDays(1)),
        nextBefore =
            startDate.takeIf {
              timeEntryRepository.existsCompletedBefore(userId = userId, before = startInclusive)
            },
        initial = initial,
    )
  }

  private fun loadProjects(entries: List<TimeEntry>): Map<Long, Project> {
    val projectIds = entries.mapNotNull { it.projectId }.distinct()
    return if (projectIds.isEmpty()) emptyMap()
    else projectRepository.findAllByTogglIdIn(projectIds).associateBy { it.togglId }
  }

  private fun loadClients(projects: Collection<Project>) =
      projects
          .mapNotNull { it.togglClientId }
          .distinct()
          .takeIf { it.isNotEmpty() }
          ?.let(clientRepository::findAllByTogglIdIn)
          ?.associateBy { it.togglId }
          .orEmpty()

  private fun TimeEntry.toView(
      project: Project?,
      clientName: String?,
      zone: ZoneId,
  ): RecentTimeEntryView {
    val localStart = start.atZone(zone)
    val localStop = requireNotNull(stop).atZone(zone)
    return RecentTimeEntryView(
        descriptionEditor =
            TimeEntryDescriptionEditorView(
                togglId = togglId,
                description = description?.takeIf { it.isNotBlank() },
            ),
        projectPicker =
            TimeEntryProjectPickerView(
                togglId = togglId,
                projectName = project?.name,
                clientName = clientName,
                projectColor = sanitizeProjectColor(project?.color),
            ),
        actions =
            TimeEntryActionsView(
                togglId = togglId,
                description = description?.takeIf { it.isNotBlank() },
                split = splitView(zone),
            ),
        timeRange = "${TIME_FORMATTER.format(localStart)} – ${TIME_FORMATTER.format(localStop)}",
        durationFormatted = formatDuration(duration),
    )
  }

  fun splitView(
      togglId: Long,
      start: Instant,
      stop: Instant,
      offset: Long,
      error: String,
  ): TimeEntrySplitView =
      buildSplitView(
          togglId = togglId,
          start = start,
          stop = stop,
          zone = credentialsService.currentTimeZone(),
          offset = offset,
          error = error,
          open = true,
      ) ?: error("A split error view requires a splittable interval")

  private fun TimeEntry.splitView(zone: ZoneId): TimeEntrySplitView? =
      buildSplitView(togglId = togglId, start = start, stop = stop, zone = zone)

  private fun buildSplitView(
      togglId: Long,
      start: Instant,
      stop: Instant?,
      zone: ZoneId,
      offset: Long? = null,
      error: String? = null,
      open: Boolean = false,
  ): TimeEntrySplitView? {
    val entryStop = stop ?: return null
    val durationSeconds = Duration.between(start, entryStop).seconds
    if (durationSeconds < 2) return null
    val localStart = start.atZone(zone)
    return TimeEntrySplitView(
        togglId = togglId,
        expectedStart = start,
        expectedStop = entryStop,
        durationSeconds = durationSeconds,
        splitOffsetSeconds = (offset ?: durationSeconds / 2).coerceIn(1, durationSeconds - 1),
        timeZone = zone.id,
        startEpochMilliseconds = start.toEpochMilli(),
        startLocalSecondOfDay = localStart.toLocalTime().toSecondOfDay(),
        startOffsetSeconds = localStart.offset.totalSeconds,
        stopOffsetSeconds = entryStop.atZone(zone).offset.totalSeconds,
        error = error,
        open = open,
    )
  }

  private fun formatDayLabel(date: LocalDate, today: LocalDate): String =
      when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(if (date.year == today.year) DAY_FORMATTER else DAY_WITH_YEAR_FORMATTER)
      }

  private fun formatRange(start: LocalDate, end: LocalDate): String =
      if (start.year == end.year) {
        "${start.format(RANGE_START_FORMATTER)}–${end.format(RANGE_END_FORMATTER)}"
      } else {
        "${start.format(RANGE_WITH_YEAR_FORMATTER)}–${end.format(RANGE_WITH_YEAR_FORMATTER)}"
      }

  private fun formatDuration(seconds: Long): String {
    val clamped = seconds.coerceAtLeast(0L)
    val hours = clamped / 3600
    val minutes = (clamped % 3600) / 60
    val remainingSeconds = clamped % 60
    return "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
  }

  companion object {
    private const val DAYS_PER_PAGE = 7L
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val DAY_FORMATTER = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val DAY_WITH_YEAR_FORMATTER =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val RANGE_START_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val RANGE_END_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    private val RANGE_WITH_YEAR_FORMATTER =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
  }
}
