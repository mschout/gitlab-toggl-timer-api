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
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspaceClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TogglSyncService(
    private val clientRepository: ClientRepository,
    private val projectRepository: ProjectRepository,
) {

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
}
