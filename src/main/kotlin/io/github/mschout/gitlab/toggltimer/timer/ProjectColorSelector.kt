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

import io.github.mschout.gitlab.toggltimer.project.ProjectRepository
import io.github.mschout.gitlab.toggltimer.project.TimeEntry
import io.github.mschout.gitlab.toggltimer.project.TimeEntryRepository
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Locale
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

private val colorSelectorLogger = KotlinLogging.logger {}

@Service
class ProjectColorSelector(
    private val timeEntryRepository: TimeEntryRepository,
    private val projectRepository: ProjectRepository,
    private val credentialsService: CurrentUserCredentialsService,
) {

  fun select(): String {
    val userId =
        runCatching { credentialsService.currentUserId() }
            .onFailure {
              colorSelectorLogger.warn(it) {
                "Failed to resolve current user while selecting a project color"
              }
            }
            .getOrNull() ?: return PROJECT_COLOR_PALETTE.random()

    val referenceEntries =
        listOfNotNull(
            latestEntry("completed") {
              timeEntryRepository
                  .findLatestCompletedForColorSelection(userId, Pageable.ofSize(1))
                  .firstOrNull()
            },
            latestEntry("running") {
              timeEntryRepository
                  .findLatestRunningForColorSelection(userId, Pageable.ofSize(1))
                  .firstOrNull()
            },
        )
    val projectIds = referenceEntries.mapNotNull(TimeEntry::projectId).distinct()
    if (projectIds.isEmpty()) return PROJECT_COLOR_PALETTE.random()

    val excludedColors =
        runCatching { projectRepository.findAllByTogglIdIn(projectIds) }
            .onFailure {
              colorSelectorLogger.warn(it) {
                "Failed to resolve recent project colors while selecting a project color"
              }
            }
            .getOrDefault(emptyList())
            .mapNotNull { it.color }
            .map { it.lowercase(Locale.ROOT) }
            .toSet()

    return PROJECT_COLOR_PALETTE.filterNot { it in excludedColors }.random()
  }

  private fun latestEntry(type: String, lookup: () -> TimeEntry?): TimeEntry? =
      runCatching(lookup)
          .onFailure {
            colorSelectorLogger.warn(it) {
              "Failed to resolve latest $type time entry while selecting a project color"
            }
          }
          .getOrNull()

  companion object {
    internal val PROJECT_COLOR_PALETTE =
        listOf(
            "#ef4444", // red-500
            "#f97316", // orange-500
            "#f59e0b", // amber-500
            "#eab308", // yellow-500
            "#84cc16", // lime-500
            "#22c55e", // green-500
            "#10b981", // emerald-500
            "#14b8a6", // teal-500
            "#06b6d4", // cyan-500
            "#0ea5e9", // sky-500
            "#3b82f6", // blue-500
            "#6366f1", // indigo-500
            "#8b5cf6", // violet-500
            "#a855f7", // purple-500
            "#d946ef", // fuchsia-500
            "#ec4899", // pink-500
            "#f43f5e", // rose-500
        )
  }
}
