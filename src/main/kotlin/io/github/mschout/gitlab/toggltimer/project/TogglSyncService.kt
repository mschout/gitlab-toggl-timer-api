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
package io.github.mschout.gitlab.toggltimer.project

import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspaceClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TogglSyncService(
    private val workspaceRepository: WorkspaceRepository,
    private val clientRepository: ClientRepository,
    private val projectRepository: ProjectRepository,
    private val timeEntryRepository: TimeEntryRepository,
) {

  @Transactional
  fun upsertWorkspaces(workspaces: List<TogglWorkspace>) {
    if (workspaces.isEmpty()) return

    val togglIds = workspaces.map { it.id }
    val existingByTogglId =
        workspaceRepository.findAllByTogglIdIn(togglIds).associateBy { it.togglId }

    workspaces.forEach { dto ->
      val existing = existingByTogglId[dto.id]
      if (existing == null) {
        workspaceRepository.save(Workspace(togglId = dto.id, name = dto.name))
      } else {
        existing.name = dto.name
        workspaceRepository.save(existing)
      }
    }
  }

  @Transactional
  fun upsertClients(workspaceId: Long, clients: List<TogglWorkspaceClient>) {
    if (clients.isEmpty()) return

    val togglIds = clients.map { it.id }
    val existingByTogglId = clientRepository.findAllByTogglIdIn(togglIds).associateBy { it.togglId }

    clients.forEach { dto ->
      val existing = existingByTogglId[dto.id]
      if (existing == null) {
        clientRepository.save(Client(togglId = dto.id, workspaceId = workspaceId, name = dto.name))
      } else {
        existing.workspaceId = workspaceId
        existing.name = dto.name
        clientRepository.save(existing)
      }
    }
  }

  @Transactional
  fun upsertProject(workspaceId: Long, project: TogglProject) {
    val togglId = requireNotNull(project.id) { "Toggl project is missing an id" }
    val name = requireNotNull(project.name) { "Toggl project is missing a name" }

    val existing = projectRepository.findByTogglId(togglId)
    if (existing == null) {
      projectRepository.save(
          Project(
              togglId = togglId,
              workspaceId = workspaceId,
              togglClientId = project.clientId,
              name = name,
              color = project.color,
              active = project.active ?: true,
          )
      )
    } else {
      existing.workspaceId = workspaceId
      existing.togglClientId = project.clientId
      existing.name = name
      existing.color = project.color
      existing.active = project.active ?: true
      projectRepository.save(existing)
    }
  }

  @Transactional
  fun upsertProjects(workspaceId: Long, projects: List<TogglProject>) {
    if (projects.isEmpty()) return
    projects.forEach { upsertProject(workspaceId, it) }
  }

  @Transactional
  fun upsertTimeEntry(userId: Long, entry: TogglTimeEntry): TimeEntry {
    val togglId = requireNotNull(entry.id) { "Toggl time entry is missing an id" }
    val start = requireNotNull(entry.start) { "Toggl time entry is missing a start" }
    val workspaceId =
        requireNotNull(entry.workspaceId) { "Toggl time entry is missing a workspaceId" }

    upsertTimeEntryMetadata(workspaceId = workspaceId, entry = entry)

    val existing = timeEntryRepository.findByTogglId(togglId)
    return if (existing == null) {
      timeEntryRepository.save(
          TimeEntry(
              togglId = togglId,
              userId = userId,
              togglUserId = entry.userId,
              workspaceId = workspaceId,
              projectId = entry.projectId,
              taskId = entry.taskId,
              description = entry.description,
              start = start,
              stop = entry.stop,
              duration = entry.duration,
              billable = entry.billable ?: false,
              tags = entry.tags.orEmpty(),
              createdWith = entry.createdWith,
              togglAt = entry.at,
              serverDeletedAt = entry.serverDeletedAt,
          )
      )
    } else {
      existing.togglUserId = entry.userId
      existing.workspaceId = workspaceId
      existing.projectId = entry.projectId
      existing.taskId = entry.taskId
      existing.description = entry.description
      existing.start = start
      existing.stop = entry.stop
      existing.duration = entry.duration
      existing.billable = entry.billable ?: false
      existing.tags = entry.tags.orEmpty()
      existing.createdWith = entry.createdWith
      existing.togglAt = entry.at
      existing.serverDeletedAt = entry.serverDeletedAt
      timeEntryRepository.save(existing)
    }
  }

  @Transactional
  fun upsertTimeEntries(userId: Long, entries: List<TogglTimeEntry>) {
    if (entries.isEmpty()) return
    entries.forEach { upsertTimeEntry(userId, it) }
  }

  private fun upsertTimeEntryMetadata(workspaceId: Long, entry: TogglTimeEntry) {
    val clientId = entry.clientId
    val clientName = entry.clientName
    if (clientId != null && !clientName.isNullOrBlank()) {
      upsertClients(
          workspaceId = workspaceId,
          clients = listOf(TogglWorkspaceClient(id = clientId, name = clientName)),
      )
    }

    val projectId = entry.projectId
    val projectName = entry.projectName
    if (projectId != null && !projectName.isNullOrBlank()) {
      upsertProject(
          workspaceId = workspaceId,
          project =
              TogglProject(
                  id = projectId,
                  name = projectName,
                  clientId = clientId,
                  workspaceId = workspaceId,
                  color = entry.projectColor,
                  active = entry.projectActive,
              ),
      )
    }
  }
}
