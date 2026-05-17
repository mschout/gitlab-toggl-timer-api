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

import io.github.mschout.gitlab.toggltimer.toggl.CreateProjectRequest as CreateTogglProjectRequest
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspaceClient
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.time.Instant
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class TogglService(
    private val togglClientFactory: TogglClientFactory,
    private val credentialsService: CurrentUserCredentialsService,
) {

  fun findOrCreateProject(
      workspaceId: Long,
      clientId: Long,
      issueNumber: Long,
      issueTitle: String,
  ): TogglProject {
    val client = togglClient()
    val projects = client.getProjects(workspaceId, name = issueNumber.toString())

    logger.info { "Found projects: $projects" }

    return projects
        .firstOrNull { it.name?.startsWith("$issueNumber -") == true }
        ?.also { logger.info { "Found project: $it" } }
        ?: run {
          logger.info { "Project not found in Toggl, creating project" }

          val createProjectRequest =
              CreateTogglProjectRequest(name = "$issueNumber - $issueTitle", clientId = clientId)

          client.createProject(workspaceId, createProjectRequest)
        }
  }

  fun startTimer(project: TogglProject, startTimerRequest: StartTimerRequest): StartTimerResult {
    val client = togglClient()
    val projectId = requireNotNull(project.id) { "Toggl project is missing an id" }
    val current = client.getCurrentTimeEntry()

    return when {
      current == null -> {
        val start = startTimerRequest.start ?: Instant.now()
        val description = startTimerRequest.description?.takeIf { it.isNotBlank() }
        val newEntry =
            TogglTimeEntry(
                workspaceId = startTimerRequest.workspaceId,
                projectId = projectId,
                start = start,
                description = description,
                duration = -1L,
                createdWith = "Gitlab Toggl Timer",
            )
        client.createTimeEntry(startTimerRequest.workspaceId, newEntry)
        StartTimerResult(startTime = start, projectName = project.name, description = description)
      }

      current.projectId == null -> {
        val workspaceId =
            requireNotNull(current.workspaceId) { "Running entry missing workspaceId" }
        val entryId = requireNotNull(current.id) { "Running entry missing id" }
        val start = requireNotNull(current.start) { "Running entry missing start" }
        val newDescription =
            startTimerRequest.description?.takeIf { it.isNotBlank() } ?: current.description
        val updated =
            TogglTimeEntry(
                workspaceId = workspaceId,
                projectId = projectId,
                start = start,
                description = newDescription,
                duration = current.duration,
                createdWith = current.createdWith ?: "Gitlab Toggl Timer",
                id = entryId,
            )
        client.updateTimeEntry(workspaceId, entryId, updated)
        StartTimerResult(
            startTime = start,
            projectName = project.name,
            description = newDescription,
        )
      }

      else -> {
        val start = requireNotNull(current.start) { "Running entry missing start" }
        val workspaceId =
            requireNotNull(current.workspaceId) { "Running entry missing workspaceId" }
        val runningProject =
            runCatching { client.getProject(workspaceId, current.projectId) }
                .onFailure {
                  logger.warn(it) {
                    "Failed to fetch Toggl project ${current.projectId} for running entry"
                  }
                }
                .getOrNull()
        StartTimerResult(
            startTime = start,
            projectName = runningProject?.name,
            description = current.description,
        )
      }
    }
  }

  fun getCurrentRunningTimer(): StartTimerResult? {
    val client = togglClient()
    val current = client.getCurrentTimeEntry() ?: return null

    // Toggl marks a running entry with a negative duration (-unix_seconds);
    // a non-negative value means the entry has already been stopped.
    if (current.duration >= 0) return null

    val start = current.start ?: return null

    val workspaceId = current.workspaceId

    val projectName =
        current.projectId?.let { projectId ->
          if (workspaceId == null) return@let null
          runCatching { client.getProject(workspaceId, projectId) }
              .onFailure {
                logger.warn(it) { "Failed to fetch Toggl project $projectId for running entry" }
              }
              .getOrNull()
              ?.name
        }

    return StartTimerResult(
        startTime = start,
        projectName = projectName,
        description = current.description,
    )
  }

  fun stopRunningTimer(): StopTimerResult? {
    val client = togglClient()
    val current = client.getCurrentTimeEntry() ?: return null

    val workspaceId =
        current.workspaceId
            ?: run {
              logger.warn { "Running entry missing workspaceId; cannot stop" }
              return null
            }

    val entryId =
        current.id
            ?: run {
              logger.warn { "Running entry missing id; cannot stop" }
              return null
            }

    val start = current.start

    client.stopTimeEntry(workspaceId, entryId)

    val elapsed =
        if (start != null) Duration.between(start, Instant.now()).seconds.coerceAtLeast(0L) else 0L

    return StopTimerResult(
        durationSeconds = elapsed,
        durationFormatted = StopTimerResult.formatHms(elapsed),
    )
  }

  fun fetchWorkspaces(apiKey: String): List<TogglWorkspace> =
      togglClientFactory.forApiKey(apiKey).getWorkspaces()

  fun fetchWorkspaces(): List<TogglWorkspace> = togglClient().getWorkspaces()

  fun fetchClients(workspaceId: Long): List<TogglWorkspaceClient> =
      togglClient().getClients(workspaceId)

  private fun togglClient(): TogglClient =
      togglClientFactory.forApiKey(credentialsService.requireTogglApiKey())

  companion object {
    private val PROJECT_COLOR_PALETTE =
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
