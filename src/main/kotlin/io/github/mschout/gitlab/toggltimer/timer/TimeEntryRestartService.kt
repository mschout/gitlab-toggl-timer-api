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

import io.github.mschout.gitlab.toggltimer.project.Project
import io.github.mschout.gitlab.toggltimer.project.ProjectRepository
import io.github.mschout.gitlab.toggltimer.project.TimeEntryRepository
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import org.springframework.stereotype.Service

sealed interface TimeEntryRestartOutcome {
  data class Started(val timer: StartTimerResult) : TimeEntryRestartOutcome

  data class Rejected(val message: String) : TimeEntryRestartOutcome

  data class StopFailed(val message: String) : TimeEntryRestartOutcome

  data class StartFailed(val message: String, val timerStateChanged: Boolean) :
      TimeEntryRestartOutcome
}

@Service
class TimeEntryRestartService(
    private val timeEntryRepository: TimeEntryRepository,
    private val projectRepository: ProjectRepository,
    private val splitOperationRepository: TimeEntrySplitOperationRepository,
    private val credentialsService: CurrentUserCredentialsService,
    private val togglService: TogglService,
) {

  fun restart(togglId: Long): TimeEntryRestartOutcome {
    val userId = credentialsService.currentUserId()
    val entry =
        timeEntryRepository.findByTogglIdAndUserId(togglId, userId)
            ?: return TimeEntryRestartOutcome.Rejected(
                "This time entry is no longer available. Refresh the page and try again."
            )
    if (entry.stop == null || entry.duration < 0 || entry.serverDeletedAt != null) {
      return TimeEntryRestartOutcome.Rejected(
          "This time entry is no longer available. Refresh the page and try again."
      )
    }
    if (splitOperationRepository.findByUserIdAndOriginalTogglId(userId, togglId) != null) {
      return TimeEntryRestartOutcome.Rejected(
          "Finish reconciling this split before restarting the entry."
      )
    }

    val project =
        entry.projectId?.let { projectId ->
          projectRepository.findByTogglIdAndWorkspaceIdAndActiveTrue(
              togglId = projectId,
              workspaceId = entry.workspaceId,
          )
              ?: return TimeEntryRestartOutcome.Rejected(
                  "The original project is no longer active in this workspace."
              )
        }

    return togglService.restartTimer(
        project = project?.toTogglProject(),
        request =
            RestartTimerRequest(
                workspaceId = entry.workspaceId,
                projectId = entry.projectId,
                description = entry.description?.takeIf { it.isNotBlank() },
            ),
    )
  }

  private fun Project.toTogglProject() =
      TogglProject(
          active = active,
          clientId = togglClientId,
          id = togglId,
          workspaceId = workspaceId,
          name = name,
          color = color,
      )
}

data class RestartTimerRequest(
    val workspaceId: Long,
    val projectId: Long?,
    val description: String?,
)
