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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
            ),
        timeRange = "${TIME_FORMATTER.format(localStart)} – ${TIME_FORMATTER.format(localStop)}",
        durationFormatted = formatDuration(duration),
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
