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
import io.github.mschout.gitlab.toggltimer.project.TogglSyncService
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.UpdateTimeEntryProjectRequest
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus

data class TimeEntryProjectPickerView(
    val togglId: Long,
    val projectName: String?,
    val clientName: String?,
    val projectColor: String?,
    val error: String? = null,
    val open: Boolean = false,
)

data class TimeEntryProjectSearchView(
    val togglId: Long,
    val query: String,
    val projects: List<TimeEntryProjectSearchResultView>,
    val error: String? = null,
)

data class TimeEntryProjectSearchResultView(
    val togglId: Long,
    val name: String,
    val clientName: String?,
    val color: String?,
    val selected: Boolean,
)

data class StoppedTimerProjectView(
    val togglId: Long,
    val name: String,
    val clientName: String?,
    val color: String?,
)

@ResponseStatus(HttpStatus.NOT_FOUND)
class TimeEntryProjectNotFoundException : RuntimeException("Time entry or project was not found")

class TogglProjectUpdateException(cause: Throwable) :
    RuntimeException("Toggl rejected the project update", cause)

class TimeEntryProjectHistoryUpdateException(cause: Throwable) :
    RuntimeException("Toggl was updated but local history could not be updated", cause)

@Service
class TimeEntryProjectService(
    private val timeEntryRepository: TimeEntryRepository,
    private val projectRepository: ProjectRepository,
    private val clientRepository: ClientRepository,
    private val togglClientFactory: TogglClientFactory,
    private val credentialsService: CurrentUserCredentialsService,
    private val togglSyncService: TogglSyncService,
) {

  fun projectsForWorkspace(workspaceId: Long): List<StoppedTimerProjectView> {
    val projects = projectRepository.findAllByWorkspaceIdAndActiveTrueOrderByNameAsc(workspaceId)
    val clientsByTogglId = loadClients(projects)
    return projects.map { project ->
      StoppedTimerProjectView(
          togglId = project.togglId,
          name = project.name,
          clientName = project.togglClientId?.let(clientsByTogglId::get),
          color = sanitizeProjectColor(project.color),
      )
    }
  }

  fun currentPicker(togglId: Long): TimeEntryProjectPickerView {
    val entry = currentUserEntry(togglId)
    val project =
        entry.projectId?.let(projectRepository::findByTogglId)?.takeIf {
          it.workspaceId == entry.workspaceId
        }
    return if (project == null) {
      TimeEntryProjectPickerView(
          togglId = togglId,
          projectName = null,
          clientName = null,
          projectColor = null,
      )
    } else {
      project.toPickerView(togglId)
    }
  }

  fun searchProjects(togglId: Long, query: String): TimeEntryProjectSearchView {
    val entry = currentUserEntry(togglId)
    val normalizedQuery = query.trim()
    val projects =
        if (normalizedQuery.isEmpty()) {
          projectRepository.findTop20ByWorkspaceIdAndActiveTrueOrderByNameAsc(entry.workspaceId)
        } else {
          projectRepository
              .findTop20ByWorkspaceIdAndActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(
                  workspaceId = entry.workspaceId,
                  name = normalizedQuery,
              )
        }
    val clientsByTogglId = loadClients(projects)

    return TimeEntryProjectSearchView(
        togglId = togglId,
        query = query,
        projects =
            projects.map { project ->
              TimeEntryProjectSearchResultView(
                  togglId = project.togglId,
                  name = project.name,
                  clientName = project.togglClientId?.let(clientsByTogglId::get),
                  color = sanitizeProjectColor(project.color),
                  selected = project.togglId == entry.projectId,
              )
            },
    )
  }

  fun updateProject(togglId: Long, projectId: Long): TimeEntryProjectPickerView {
    val userId = credentialsService.currentUserId()
    val entry = currentUserEntry(togglId = togglId, userId = userId)
    val project =
        projectRepository.findByTogglIdAndWorkspaceIdAndActiveTrue(
            togglId = projectId,
            workspaceId = entry.workspaceId,
        ) ?: throw TimeEntryProjectNotFoundException()

    if (entry.projectId == project.togglId) return project.toPickerView(togglId)

    val request =
        UpdateTimeEntryProjectRequest(workspaceId = entry.workspaceId, projectId = project.togglId)
    val updatedEntry =
        try {
          togglClientFactory
              .forApiKey(credentialsService.requireTogglApiKey())
              .updateTimeEntryProject(entry.workspaceId, togglId, request)
        } catch (ex: Exception) {
          throw TogglProjectUpdateException(ex)
        }

    try {
      togglSyncService.upsertTimeEntry(userId, updatedEntry)
    } catch (ex: Exception) {
      throw TimeEntryProjectHistoryUpdateException(ex)
    }

    return project.toPickerView(togglId)
  }

  private fun currentUserEntry(togglId: Long): TimeEntry =
      currentUserEntry(togglId = togglId, userId = credentialsService.currentUserId())

  private fun currentUserEntry(togglId: Long, userId: Long): TimeEntry =
      timeEntryRepository.findByTogglIdAndUserId(togglId = togglId, userId = userId)
          ?: throw TimeEntryProjectNotFoundException()

  private fun loadClients(projects: Collection<Project>): Map<Long, String> {
    val clientIds = projects.mapNotNull { it.togglClientId }.distinct()
    return if (clientIds.isEmpty()) emptyMap()
    else
        clientRepository.findAllByTogglIdIn(clientIds).associate { client ->
          client.togglId to client.name
        }
  }

  private fun Project.toPickerView(togglId: Long): TimeEntryProjectPickerView =
      TimeEntryProjectPickerView(
          togglId = togglId,
          projectName = name,
          clientName = togglClientId?.let(clientRepository::findByTogglId)?.name,
          projectColor = sanitizeProjectColor(color),
      )
}

internal fun sanitizeProjectColor(color: String?): String? =
    color?.takeIf(PROJECT_COLOR_PATTERN::matches)

private val PROJECT_COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")
