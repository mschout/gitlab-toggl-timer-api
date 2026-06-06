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

import io.github.mschout.gitlab.toggltimer.gitlab.GitLabService
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import java.time.LocalDate
import org.springframework.stereotype.Service

@Service
class TimerService(
    private val gitLabService: GitLabService,
    private val togglService: TogglService,
) {

  fun startTimer(startTimerRequest: StartTimerRequest): StartTimerResult {
    val project =
        startTimerRequest.issue()?.let { issue ->
          val clientId =
              requireNotNull(startTimerRequest.clientId) {
                "A Toggl client is required to create a project for a GitLab issue"
              }
          val issueTitle = gitLabService.getGitlabIssueTitle(issue)
          togglService.findOrCreateProject(
              startTimerRequest.workspaceId,
              clientId,
              issue.issueNumber,
              issueTitle,
          )
        }

    return togglService.startTimer(project, startTimerRequest)
  }

  fun stopTimer(description: String? = null): StopTimerResult? =
      togglService.stopRunningTimer(description)

  fun syncHistory(days: Int): SyncHistoryResult {
    val end = LocalDate.now()
    val start = end.minusDays(days.toLong())
    val count = togglService.backfillTimeEntries(start, end)
    return SyncHistoryResult(count = count, startDate = start, endDate = end)
  }

  fun syncProjects(): SyncProjectsResult = togglService.backfillProjects()

  fun createProject(createProjectRequest: CreateProjectRequest): TogglProject {
    val issue = createProjectRequest.issue()
    val issueTitle = gitLabService.getGitlabIssueTitle(issue)

    return togglService.findOrCreateProject(
        createProjectRequest.workspaceId,
        createProjectRequest.clientId,
        issue.issueNumber,
        issueTitle,
    )
  }
}
