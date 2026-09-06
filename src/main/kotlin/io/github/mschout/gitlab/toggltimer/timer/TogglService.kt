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

import io.github.mschout.gitlab.toggltimer.project.TogglSyncService
import io.github.mschout.gitlab.toggltimer.toggl.CreateProjectRequest as CreateTogglProjectRequest
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspaceClient
import io.github.mschout.gitlab.toggltimer.toggl.streamProjects
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class TogglService(
    private val togglClientFactory: TogglClientFactory,
    private val credentialsService: CurrentUserCredentialsService,
    private val togglSyncService: TogglSyncService,
    private val projectColorSelector: ProjectColorSelector,
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

    val project =
        projects
            .firstOrNull { it.name?.startsWith("$issueNumber -") == true }
            ?.also { logger.info { "Found project: $it" } }
            ?: run {
              logger.info { "Project not found in Toggl, creating project" }

              val createProjectRequest =
                  CreateTogglProjectRequest(
                      name = "$issueNumber - $issueTitle",
                      clientId = clientId,
                  )

              client
                  .createProject(workspaceId, createProjectRequest)
                  .copy(color = createProjectRequest.color ?: projectColorSelector.select())
            }

    runCatching { togglSyncService.upsertProject(workspaceId, project) }
        .onFailure { logger.warn(it) { "Failed to sync Toggl project to Postgres" } }

    return project
  }

  /**
   * Starts (or adopts) a running Toggl timer. When [project] is null the timer is started without
   * an associated Toggl project — this is the "workspace only" flow where no GitLab issue was
   * provided. A non-null [project] is attached to the new (or an already-running, project-less)
   * entry as before.
   */
  fun startTimer(project: TogglProject?, startTimerRequest: StartTimerRequest): StartTimerResult {
    val client = togglClient()
    val projectId = project?.id
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
        val created = client.createTimeEntry(startTimerRequest.workspaceId, newEntry)
        shadowWriteProject(startTimerRequest.workspaceId, project)
        shadowWriteTimeEntry(created)
        created.toRunningTimerResult(
            fallbackStart = start,
            project = project,
            fallbackDescription = description,
        )
      }

      current.projectId == null && projectId != null -> {
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
        val result = client.updateTimeEntry(workspaceId, entryId, updated)
        shadowWriteProject(workspaceId, project)
        shadowWriteTimeEntry(result)
        result.toRunningTimerResult(
            fallbackStart = start,
            project = project,
            fallbackDescription = newDescription,
        )
      }

      else -> {
        val start = requireNotNull(current.start) { "Running entry missing start" }
        val workspaceId =
            requireNotNull(current.workspaceId) { "Running entry missing workspaceId" }
        val runningProject =
            current.projectId?.let { runningProjectId ->
              runCatching { client.getProject(workspaceId, runningProjectId) }
                  .onFailure {
                    logger.warn(it) {
                      "Failed to fetch Toggl project $runningProjectId for running entry"
                    }
                  }
                  .getOrNull()
            }
        shadowWriteProject(workspaceId, runningProject)
        shadowWriteTimeEntry(current)
        current.toRunningTimerResult(fallbackStart = start, project = runningProject)
      }
    }
  }

  /** Stops any running timer and starts a new entry from the supplied historical metadata. */
  fun restartTimer(project: TogglProject?, request: RestartTimerRequest): TimeEntryRestartOutcome {
    val client = togglClient()
    val current =
        try {
          client.getCurrentTimeEntry()
        } catch (exception: Exception) {
          logger.warn(exception) { "Failed to check the current Toggl timer before restarting" }
          return TimeEntryRestartOutcome.StopFailed(
              "Could not check the current Toggl timer. Nothing was changed."
          )
        }

    var timerStateChanged = false
    if (current != null && current.duration < 0) {
      val workspaceId = current.workspaceId
      val entryId = current.id
      if (workspaceId == null || entryId == null) {
        return TimeEntryRestartOutcome.StopFailed(
            "The current Toggl timer could not be stopped. Nothing was changed."
        )
      }
      val stopped =
          try {
            client.stopTimeEntry(workspaceId, entryId)
          } catch (exception: Exception) {
            logger.warn(exception) { "Failed to stop Toggl time entry $entryId before restarting" }
            return TimeEntryRestartOutcome.StopFailed(
                "Could not stop the current Toggl timer. Nothing was changed."
            )
          }
      shadowWriteTimeEntry(stopped)
      timerStateChanged = true
    }

    val start = Instant.now()
    val newEntry =
        TogglTimeEntry(
            workspaceId = request.workspaceId,
            projectId = request.projectId,
            start = start,
            description = request.description,
            duration = -1L,
            createdWith = "Gitlab Toggl Timer",
        )
    val created =
        try {
          client.createTimeEntry(request.workspaceId, newEntry)
        } catch (exception: Exception) {
          logger.warn(exception) { "Failed to restart Toggl timer from a recent entry" }
          return TimeEntryRestartOutcome.StartFailed(
              message =
                  if (timerStateChanged) {
                    "The previous timer was stopped, but the selected timer could not start."
                  } else {
                    "The selected timer could not start."
                  },
              timerStateChanged = timerStateChanged,
          )
        }

    shadowWriteProject(request.workspaceId, project)
    shadowWriteTimeEntry(created)
    return TimeEntryRestartOutcome.Started(
        created.toRunningTimerResult(
            fallbackStart = start,
            project = project,
            fallbackDescription = request.description,
        )
    )
  }

  fun getCurrentRunningTimer(): StartTimerResult? {
    val client = togglClient()
    val current = client.getCurrentTimeEntry() ?: return null

    // Toggl marks a running entry with a negative duration (-unix_seconds);
    // a non-negative value means the entry has already been stopped.
    if (current.duration >= 0) return null

    val start = current.start ?: return null
    if (current.id == null) return null

    val workspaceId = current.workspaceId ?: return null

    val project =
        current.projectId?.let { projectId ->
          runCatching { client.getProject(workspaceId, projectId) }
              .onFailure {
                logger.warn(it) { "Failed to fetch Toggl project $projectId for running entry" }
              }
              .getOrNull()
        }

    shadowWriteProject(workspaceId, project)
    shadowWriteTimeEntry(current)
    return current.toRunningTimerResult(fallbackStart = start, project = project)
  }

  /**
   * Stops the currently running Toggl timer.
   *
   * When [description] is non-null it replaces the entry's description in Toggl (a blank value
   * clears it); a null value leaves the existing description untouched. The final entry — including
   * any description change — is shadow-written to Postgres.
   */
  fun stopRunningTimer(description: String? = null): StopTimerResult? {
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

    var stopped = client.stopTimeEntry(workspaceId, entryId)

    if (description != null) {
      val newDescription = description.takeIf { it.isNotBlank() }
      if (newDescription != stopped.description) {
        stopped =
            runCatching {
                  client.updateTimeEntry(
                      workspaceId,
                      entryId,
                      stopped.copy(description = newDescription),
                  )
                }
                .onFailure {
                  logger.warn(it) { "Failed to update description on stopped Toggl entry $entryId" }
                }
                .getOrDefault(stopped.copy(description = newDescription))
      }
    }

    shadowWriteTimeEntry(stopped)

    val elapsed =
        if (start != null) Duration.between(start, Instant.now()).seconds.coerceAtLeast(0L) else 0L

    return StopTimerResult(
        durationSeconds = elapsed,
        durationFormatted = StopTimerResult.formatHms(elapsed),
    )
  }

  fun fetchWorkspaces(apiKey: String): List<TogglWorkspace> =
      togglClientFactory.forApiKey(apiKey).getWorkspaces()

  fun fetchWorkspaces(): List<TogglWorkspace> {
    val workspaces = togglClient().getWorkspaces()
    runCatching { togglSyncService.upsertWorkspaces(workspaces) }
        .onFailure { logger.warn(it) { "Failed to sync Toggl workspaces to Postgres" } }
    return workspaces
  }

  fun fetchClients(workspaceId: Long): List<TogglWorkspaceClient> {
    val clients = togglClient().getClients(workspaceId)
    runCatching { togglSyncService.upsertClients(workspaceId, clients) }
        .onFailure { logger.warn(it) { "Failed to sync Toggl clients to Postgres" } }
    return clients
  }

  fun backfillTimeEntries(startDate: LocalDate, endDate: LocalDate): Int {
    val userId = credentialsService.currentUserId()
    val entries =
        togglClient().getTimeEntries(startDate = startDate.toString(), endDate = endDate.toString())
    runCatching { togglSyncService.upsertTimeEntries(userId, entries) }
        .onFailure { logger.warn(it) { "Failed to backfill Toggl time entries to Postgres" } }
    return entries.size
  }

  fun backfillProjects(): SyncProjectsResult {
    val client = togglClient()
    val workspaces = client.getWorkspaces()
    runCatching { togglSyncService.upsertWorkspaces(workspaces) }
        .onFailure { logger.warn(it) { "Failed to sync Toggl workspaces to Postgres" } }

    var total = 0
    workspaces.forEach { workspace ->
      val projects = client.streamProjects(workspace.id).toList()
      runCatching { togglSyncService.upsertProjects(workspace.id, projects) }
          .onFailure {
            logger.warn(it) {
              "Failed to backfill Toggl projects for workspace ${workspace.id} to Postgres"
            }
          }
      total += projects.size
    }
    return SyncProjectsResult(count = total, workspaces = workspaces.size)
  }

  private fun shadowWriteTimeEntry(entry: TogglTimeEntry) {
    val userId = credentialsService.currentUserId()
    runCatching { togglSyncService.upsertTimeEntry(userId, entry) }
        .onFailure { logger.warn(it) { "Failed to sync Toggl time entry to Postgres" } }
  }

  private fun shadowWriteProject(workspaceId: Long, project: TogglProject?) {
    if (project == null) return
    runCatching { togglSyncService.upsertProject(workspaceId, project) }
        .onFailure { logger.warn(it) { "Failed to sync running Toggl project to Postgres" } }
  }

  private fun TogglTimeEntry.toRunningTimerResult(
      fallbackStart: Instant,
      project: TogglProject?,
      fallbackDescription: String? = description,
  ) =
      StartTimerResult(
          togglId = requireNotNull(id) { "Running entry missing id" },
          startTime = start ?: fallbackStart,
          projectName = project?.name ?: projectName,
          clientName = clientName,
          projectColor = sanitizeProjectColor(project?.color ?: projectColor),
          description = description ?: fallbackDescription,
      )

  private fun togglClient(): TogglClient =
      togglClientFactory.forApiKey(credentialsService.requireTogglApiKey())
}
