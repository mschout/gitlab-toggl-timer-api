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

import io.github.mschout.gitlab.toggltimer.gitlab.GitLabIssueNotFoundException
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspaceClient
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

private val logger = KotlinLogging.logger {}

@Controller
@RequestMapping("/timer")
class TimerWebController(
    private val timerService: TimerService,
    private val credentialsService: CurrentUserCredentialsService,
    private val togglService: TogglService,
    private val timeEntryHistoryService: TimeEntryHistoryService,
    private val timeEntryDescriptionService: TimeEntryDescriptionService,
    private val timeEntryStartService: TimeEntryStartService,
    private val timeEntryRestartService: TimeEntryRestartService,
    private val timeEntryProjectService: TimeEntryProjectService,
    private val timeEntryDeletionService: TimeEntryDeletionService,
    private val timeEntrySplitWorkflow: TimeEntrySplitWorkflow,
    private val clock: Clock,
) {

  @GetMapping
  fun index(model: Model): String {
    val form =
        model.getAttribute("form") as? TimerForm
            ?: TimerForm(workspaceId = credentialsService.currentTogglWorkspaceId()).also {
              model.addAttribute("form", it)
            }
    model.addAttribute("message", "Welcome to the Timer Page!")
    model.addAttribute("formExpanded", false)
    loadDropdownData(form, model)
    loadHistoryData(model)
    loadTotalsData(model)

    val running =
        runCatching { togglService.getCurrentRunningTimer() }
            .onFailure { logger.warn(it) { "Failed to fetch current Toggl timer" } }
            .getOrNull()
    if (running != null) {
      addRunningTimer(running, model)
    } else {
      addStoppedTimer(model)
    }
    return "timer-index"
  }

  @GetMapping("/create-project")
  fun createProject(
      @RequestParam issueUrl: String,
      @RequestParam workspaceId: Long,
      @RequestParam clientId: Long,
  ): ModelAndView {
    val project = timerService.createProject(CreateProjectRequest(issueUrl, workspaceId, clientId))
    return ModelAndView("create-project").apply { addObject("project", project) }
  }

  @PostMapping("/create-project")
  fun createProjectSubmit(
      @Valid @ModelAttribute("form") form: TimerForm,
      bindingResult: BindingResult,
      @RequestHeader(name = "HX-Request", required = false) hxRequest: Boolean = false,
      model: Model,
      response: HttpServletResponse,
  ): String {
    if (form.issueUrl.isBlank()) {
      bindingResult.rejectValue("issueUrl", "NotBlank", "A GitLab issue URL is required.")
    }
    if (form.clientId == null) {
      bindingResult.rejectValue("clientId", "NotNull", "A Toggl client is required.")
    }
    if (bindingResult.hasErrors()) {
      return formErrorView(form, model, hxRequest, response)
    }
    val project =
        try {
          timerService.createProject(form.toCreateProjectRequest())
        } catch (exception: GitLabIssueNotFoundException) {
          logger.warn { exception.message ?: "GitLab issue not found" }
          bindingResult.rejectValue("issueUrl", "NotFound", "GitLab issue not found.")
          return formErrorView(form, model, hxRequest, response)
        }
    model.addAttribute("project", project)
    if (hxRequest) response.setHeader("HX-Trigger", "issueUrlConsumed")
    return if (hxRequest) "create-project :: success-alert" else "create-project"
  }

  @GetMapping("/start")
  fun startTimer(
      @RequestParam issueUrl: String,
      @RequestParam workspaceId: Long,
      @RequestParam clientId: Long,
  ): ModelAndView {
    val request =
        StartTimerRequest(issueUrl = issueUrl, workspaceId = workspaceId, clientId = clientId)
    val result = timerService.startTimer(request)
    return ModelAndView("start-timer").apply {
      addObject("runningTimer", runningTimerView(result))
      addObject("timeTotals", timeEntryHistoryService.currentTotals())
    }
  }

  @PostMapping("/start")
  fun startTimerSubmit(
      @Valid @ModelAttribute("form") form: TimerForm,
      bindingResult: BindingResult,
      @RequestHeader(name = "HX-Request", required = false) hxRequest: Boolean = false,
      model: Model,
      response: HttpServletResponse,
  ): String {
    // The issue URL is optional for Start Timer (a bare timer just tracks the workspace), but if
    // one is supplied we need a client to find or create its Toggl project.
    if (form.issueUrl.isNotBlank() && form.clientId == null) {
      bindingResult.rejectValue(
          "clientId",
          "NotNull",
          "A Toggl client is required when tracking a GitLab issue.",
      )
    }
    if (bindingResult.hasErrors()) {
      return formErrorView(form, model, hxRequest, response)
    }
    val result =
        try {
          timerService.startTimer(form.toStartTimerRequest())
        } catch (exception: GitLabIssueNotFoundException) {
          logger.warn { exception.message ?: "GitLab issue not found" }
          bindingResult.rejectValue("issueUrl", "NotFound", "GitLab issue not found.")
          return formErrorView(form, model, hxRequest, response)
        }
    addRunningTimer(result, model)
    loadTotalsData(model)
    if (hxRequest && form.issueUrl.isNotBlank()) {
      response.setHeader("HX-Trigger", "issueUrlConsumed")
    }
    return if (hxRequest) "start-timer :: result-card" else "start-timer"
  }

  @PostMapping("/stop")
  fun stopTimerSubmit(
      @RequestParam(required = false) description: String? = null,
      @RequestHeader(name = "HX-Request", required = false) hxRequest: Boolean = false,
      model: Model,
      response: HttpServletResponse,
  ): String {
    val result = timerService.stopTimer(description)
    if (result != null) {
      model.addAttribute("durationFormatted", result.durationFormatted)
      model.addAttribute("stopped", true)
      if (hxRequest) response.setHeader("HX-Trigger", "timeEntriesChanged")
    } else {
      model.addAttribute("stopped", false)
    }
    addStoppedTimer(model)
    loadTotalsData(model)
    return if (hxRequest) "stop-timer :: result-card" else "stop-timer"
  }

  @GetMapping("/totals")
  fun totals(model: Model): String {
    loadTotalsData(model)
    return "timer-index :: time-totals"
  }

  @GetMapping("/entries")
  fun recentEntries(model: Model): String {
    loadHistoryData(model)
    return "timer-index :: recent-entries"
  }

  @GetMapping("/entries/page")
  fun olderEntries(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) before: LocalDate,
      model: Model,
  ): String {
    model.addAttribute("historyPage", timeEntryHistoryService.pageBefore(before))
    return "timer-index :: history-page"
  }

  @PostMapping("/entries/{togglId}/description")
  fun updateEntryDescription(
      @PathVariable togglId: Long,
      @RequestParam description: String,
      model: Model,
  ): String {
    val descriptionEditor =
        try {
          timeEntryDescriptionService.updateDescription(togglId, description)
        } catch (ex: TogglDescriptionUpdateException) {
          logger.warn(ex) { "Failed to update Toggl time entry $togglId description" }
          TimeEntryDescriptionEditorView(
              togglId = togglId,
              description = description,
              error = "Could not save to Toggl. Press Enter to retry.",
              editing = true,
          )
        } catch (ex: TimeEntryHistoryUpdateException) {
          logger.warn(ex) { "Failed to sync Toggl time entry $togglId description to Postgres" }
          TimeEntryDescriptionEditorView(
              togglId = togglId,
              description = description,
              error = "Saved to Toggl, but local history is out of sync. Press Enter to retry.",
              editing = true,
          )
        }
    model.addAttribute("descriptionEditor", descriptionEditor)
    return "fragments/time-entry-description :: description-editor"
  }

  @PostMapping("/entries/{togglId}/start")
  fun updateEntryStart(
      @PathVariable togglId: Long,
      @RequestParam expectedStart: Instant,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
      @RequestParam startTime: String,
      model: Model,
      response: HttpServletResponse,
  ): String {
    val command =
        UpdateTimeEntryStartCommand(
            togglId = togglId,
            expectedStart = expectedStart,
            startDate = startDate,
            startTime = startTime,
        )
    val outcome =
        try {
          timeEntryStartService.updateStart(command)
        } catch (exception: TimeEntryStartValidationException) {
          return startEditorError(command, exception.message.orEmpty(), model, response)
        } catch (exception: TogglStartUpdateException) {
          logger.warn(exception) { "Failed to update Toggl time entry $togglId start" }
          return startEditorError(
              command,
              "Could not save the start time to Toggl. Try again.",
              model,
              response,
          )
        }

    when (outcome) {
      is TimeEntryStartUpdateOutcome.Saved -> {
        addRunningTimer(outcome.entry.toStartTimerResult(), model)
        model.addAttribute(
            "timerNotification",
            if (outcome.historySynchronized) null
            else
                "Start time was saved to Toggl, but local history is out of sync. " +
                    "It will catch up automatically.",
        )
      }
      is TimeEntryStartUpdateOutcome.Unchanged -> {
        addRunningTimer(outcome.entry.toStartTimerResult(), model)
        model.addAttribute("timerNotification", null)
      }
      is TimeEntryStartUpdateOutcome.Stale -> {
        addActualTimer(outcome.currentEntry, model)
        model.addAttribute(
            "timerNotification",
            "The running timer changed in Toggl. Your start time edit was not applied.",
        )
      }
    }

    return "start-timer :: start-update-response"
  }

  @PostMapping("/entries/{togglId}/restart")
  fun restartEntry(
      @PathVariable togglId: Long,
      model: Model,
      response: HttpServletResponse,
  ): String {
    val outcome = timeEntryRestartService.restart(togglId)
    val notification =
        when (outcome) {
          is TimeEntryRestartOutcome.Started -> {
            addRunningTimer(outcome.timer, model)
            null
          }
          is TimeEntryRestartOutcome.Rejected -> {
            addCurrentTimer(model)
            outcome.message
          }
          is TimeEntryRestartOutcome.StopFailed -> {
            addCurrentTimer(model)
            outcome.message
          }
          is TimeEntryRestartOutcome.StartFailed -> {
            if (outcome.timerStateChanged) addStoppedTimer(model) else addCurrentTimer(model)
            outcome.message
          }
        }
    model.addAttribute("timerNotification", notification)
    response.setHeader("HX-Retarget", "#result")
    response.setHeader("HX-Reswap", "outerHTML")
    if (
        outcome is TimeEntryRestartOutcome.Started ||
            outcome is TimeEntryRestartOutcome.StartFailed && outcome.timerStateChanged
    ) {
      response.setHeader("HX-Trigger", "timeEntriesChanged, timeTotalsChanged")
    }
    return "start-timer :: start-update-response"
  }

  private fun startEditorError(
      command: UpdateTimeEntryStartCommand,
      error: String,
      model: Model,
      response: HttpServletResponse,
  ): String {
    val zone = credentialsService.currentTimeZone()
    model.addAttribute(
        "startEditor",
        TimeEntryStartEditorView(
            togglId = command.togglId,
            expectedStart = command.expectedStart,
            startDate = command.startDate,
            startTime = command.startTime,
            today = LocalDate.now(clock.withZone(zone)),
            timeZone = zone.id,
            error = error,
            open = true,
        ),
    )
    response.setHeader("HX-Retarget", "#running-timer-start-dialog-${command.togglId}")
    response.setHeader("HX-Reswap", "outerHTML")
    return "start-timer :: start-editor"
  }

  @DeleteMapping("/entries/{togglId}")
  fun deleteEntry(
      @PathVariable togglId: Long,
      model: Model,
      response: HttpServletResponse,
  ): String {
    val actions =
        try {
          timeEntryDeletionService.delete(togglId)
          null
        } catch (exception: TogglTimeEntryDeletionException) {
          logger.warn(exception) { "Failed to delete Toggl time entry $togglId" }
          TimeEntryActionsView(
              togglId = togglId,
              description = exception.description,
              error = "Could not delete from Toggl. Try again.",
              open = true,
          )
        } catch (exception: TimeEntryHistoryDeletionException) {
          logger.warn(exception) { "Failed to delete Toggl time entry $togglId from Postgres" }
          TimeEntryActionsView(
              togglId = togglId,
              description = exception.description,
              error = "Deleted from Toggl, but local history could not be removed. Try again.",
              open = true,
          )
        } catch (exception: TimeEntrySplitInProgressException) {
          TimeEntryActionsView(
              togglId = togglId,
              description = null,
              deleteDisabledReason = "Finish reconciling this split before deleting the entry.",
          )
        }

    if (actions != null) {
      model.addAttribute("entryActions", actions)
      return "fragments/time-entry-actions :: entry-actions"
    }

    loadHistoryData(model)
    response.setHeader("HX-Retarget", "#recent-time-entries")
    response.setHeader("HX-Reswap", "outerHTML")
    response.setHeader("HX-Trigger", "timeTotalsChanged")
    return "timer-index :: recent-entries"
  }

  @DeleteMapping("/running/{togglId}")
  fun deleteRunningEntry(
      @PathVariable togglId: Long,
      model: Model,
      response: HttpServletResponse,
  ): String {
    val actions =
        try {
          timeEntryDeletionService.deleteRunning(togglId)
          null
        } catch (exception: TogglTimeEntryDeletionException) {
          logger.warn(exception) { "Failed to stop and delete running Toggl time entry $togglId" }
          TimeEntryActionsView(
              togglId = togglId,
              description = exception.description,
              error = "Could not delete from Toggl. Try again.",
              open = true,
          )
        } catch (exception: TimeEntryHistoryDeletionException) {
          logger.warn(exception) {
            "Failed to delete running Toggl time entry $togglId from Postgres"
          }
          TimeEntryActionsView(
              togglId = togglId,
              description = exception.description,
              error = "Deleted from Toggl, but local history could not be removed. Try again.",
              open = true,
          )
        } catch (exception: TimeEntrySplitInProgressException) {
          val message = "Finish reconciling this split before deleting the entry."
          TimeEntryActionsView(
              togglId = togglId,
              description = null,
              splitDisabledReason = message,
              deleteDisabledReason = message,
              splitStatus = message,
          )
        }

    if (actions != null) {
      model.addAttribute("entryActions", actions)
      return "fragments/running-timer-actions :: running-timer-actions"
    }

    addStoppedTimer(model)
    response.setHeader("HX-Retarget", "#result")
    response.setHeader("HX-Reswap", "innerHTML")
    response.setHeader("HX-Trigger", "timeEntriesChanged, timeTotalsChanged")
    return "stop-timer :: result-card"
  }

  @GetMapping("/running/{togglId}/split")
  fun runningSplitDialog(
      @PathVariable togglId: Long,
      model: Model,
      response: HttpServletResponse,
  ): String {
    val preparation = timeEntrySplitWorkflow.prepareRunning(togglId)
    if (preparation is RunningTimeEntrySplitPreparation.Rejected) {
      return refreshCurrentTimerAfterSplit(
          model = model,
          response = response,
          notification = preparation.message,
      )
    }
    val snapshot = (preparation as RunningTimeEntrySplitPreparation.Ready).snapshot
    val actions =
        TimeEntryActionsView(
            togglId = togglId,
            description = null,
            split =
                timeEntryHistoryService.splitView(
                    togglId = togglId,
                    start = snapshot.start,
                    stop = snapshot.snapshotEnd,
                    open = true,
                    running = true,
                ),
        )
    model.addAttribute("runningTimerSplitEnableAt", snapshot.start.plusSeconds(2).toEpochMilli())
    model.addAttribute("entryActions", actions)
    return "fragments/running-timer-actions :: running-timer-actions"
  }

  @PostMapping("/running/{togglId}/split")
  fun splitRunningEntry(
      @PathVariable togglId: Long,
      @RequestParam expectedStart: Instant,
      @RequestParam snapshotEnd: Instant,
      @RequestParam splitOffsetSeconds: Long,
      model: Model,
      response: HttpServletResponse,
  ): String {
    val outcome =
        try {
          timeEntrySplitWorkflow.splitRunning(
              SplitRunningTimeEntryCommand(
                  togglId = togglId,
                  expectedStart = expectedStart,
                  snapshotEnd = snapshotEnd,
                  splitOffsetSeconds = splitOffsetSeconds,
              )
          )
        } catch (exception: IllegalArgumentException) {
          SplitTimeEntryOutcome.Rejected(
              exception.message ?: "Choose a valid split point inside this time entry."
          )
        }

    if (outcome == SplitTimeEntryOutcome.Completed || outcome is SplitTimeEntryOutcome.Rejected) {
      return refreshCurrentTimerAfterSplit(
          model = model,
          response = response,
          notification = (outcome as? SplitTimeEntryOutcome.Rejected)?.message,
      )
    }

    val message =
        when (outcome) {
          is SplitTimeEntryOutcome.RecoveryPending -> outcome.message
          is SplitTimeEntryOutcome.NeedsReview -> outcome.message
          SplitTimeEntryOutcome.Completed,
          is SplitTimeEntryOutcome.Rejected -> error("Handled above")
        }
    val split =
        timeEntryHistoryService.splitView(
            togglId = togglId,
            start = expectedStart,
            stop = snapshotEnd,
            offset = splitOffsetSeconds,
            error = message,
            open = true,
            running = true,
        )
    model.addAttribute(
        "entryActions",
        TimeEntryActionsView(
            togglId = togglId,
            description = null,
            split = split,
            splitDisabledReason = message,
            deleteDisabledReason = message,
            splitStatus = message,
            splitPolling = outcome is SplitTimeEntryOutcome.RecoveryPending,
            splitNeedsReview = outcome is SplitTimeEntryOutcome.NeedsReview,
        ),
    )
    model.addAttribute("runningTimerSplitEnableAt", null)
    return "fragments/running-timer-actions :: running-timer-actions"
  }

  @GetMapping("/running/{togglId}/split/status")
  fun runningSplitStatus(
      @PathVariable togglId: Long,
      model: Model,
      response: HttpServletResponse,
  ): String {
    val status = timeEntrySplitWorkflow.operationStatus(togglId)
    if (status == null) {
      return refreshCurrentTimerAfterSplit(
          model = model,
          response = response,
          notification = "The running timer split finished.",
      )
    }
    model.addAttribute(
        "entryActions",
        TimeEntryActionsView(
            togglId = togglId,
            description = null,
            splitDisabledReason = status.message,
            deleteDisabledReason = status.message,
            splitStatus = status.message,
            splitPolling = status.pending,
            splitNeedsReview = status.needsReview,
        ),
    )
    model.addAttribute("runningTimerSplitEnableAt", null)
    return "fragments/running-timer-actions :: running-timer-actions"
  }

  @PostMapping("/entries/{togglId}/split")
  fun splitEntry(
      @PathVariable togglId: Long,
      @RequestParam expectedStart: Instant,
      @RequestParam expectedStop: Instant,
      @RequestParam splitOffsetSeconds: Long,
      model: Model,
      response: HttpServletResponse,
  ): String {
    val outcome =
        try {
          timeEntrySplitWorkflow.split(
              SplitTimeEntryCommand(
                  togglId = togglId,
                  expectedStart = expectedStart,
                  expectedStop = expectedStop,
                  splitOffsetSeconds = splitOffsetSeconds,
              )
          )
        } catch (exception: IllegalArgumentException) {
          SplitTimeEntryOutcome.Rejected(
              exception.message ?: "Choose a split point inside this time entry."
          )
        }

    if (outcome == SplitTimeEntryOutcome.Completed) {
      loadHistoryData(model)
      response.setHeader("HX-Retarget", "#recent-time-entries")
      response.setHeader("HX-Reswap", "outerHTML")
      response.setHeader("HX-Trigger", "timeTotalsChanged")
      return "timer-index :: recent-entries"
    }

    val message =
        when (outcome) {
          is SplitTimeEntryOutcome.Rejected -> outcome.message
          is SplitTimeEntryOutcome.RecoveryPending -> outcome.message
          is SplitTimeEntryOutcome.NeedsReview -> outcome.message
          SplitTimeEntryOutcome.Completed -> error("Handled above")
        }
    val split =
        timeEntryHistoryService.splitView(
            togglId = togglId,
            start = expectedStart,
            stop = expectedStop,
            offset = splitOffsetSeconds,
            error = message,
            open = true,
        )
    model.addAttribute(
        "entryActions",
        TimeEntryActionsView(togglId = togglId, description = null, split = split),
    )
    return "fragments/time-entry-actions :: entry-actions"
  }

  @GetMapping("/entries/{togglId}/projects")
  fun searchEntryProjects(
      @PathVariable togglId: Long,
      @RequestParam(defaultValue = "") query: String,
      model: Model,
  ): String {
    val projectSearch =
        try {
          timeEntryProjectService.searchProjects(togglId = togglId, query = query)
        } catch (ex: TimeEntryProjectNotFoundException) {
          throw ex
        } catch (ex: Exception) {
          logger.warn(ex) { "Failed to search Postgres projects for time entry $togglId" }
          TimeEntryProjectSearchView(
              togglId = togglId,
              query = query,
              projects = emptyList(),
              error = "Could not search projects. Try again.",
          )
        }
    model.addAttribute("projectSearch", projectSearch)
    return "fragments/time-entry-project :: project-results"
  }

  @PostMapping("/entries/{togglId}/project")
  fun updateEntryProject(
      @PathVariable togglId: Long,
      @RequestParam projectId: Long,
      model: Model,
  ): String {
    val projectPicker =
        try {
          timeEntryProjectService.updateProject(togglId = togglId, projectId = projectId)
        } catch (ex: TogglProjectUpdateException) {
          logger.warn(ex) { "Failed to update Toggl time entry $togglId project" }
          timeEntryProjectService
              .currentPicker(togglId)
              .copy(error = "Could not save to Toggl. Choose a project to retry.", open = true)
        } catch (ex: TimeEntryProjectHistoryUpdateException) {
          logger.warn(ex) { "Failed to sync Toggl time entry $togglId project to Postgres" }
          timeEntryProjectService
              .currentPicker(togglId)
              .copy(
                  error =
                      "Saved to Toggl, but local history is out of sync. Choose a project to retry.",
                  open = true,
              )
        }
    model.addAttribute("projectPicker", projectPicker)
    return "fragments/time-entry-project :: project-picker"
  }

  @GetMapping("/clients")
  fun clientsFragment(@RequestParam(required = false) workspaceId: Long?, model: Model): String {
    val result = workspaceId?.let { runCatching { togglService.fetchClients(it) } }
    val clients = result?.getOrDefault(emptyList()) ?: emptyList()
    val error =
        result?.exceptionOrNull()?.let { ex ->
          logger.warn(ex) { "Failed to fetch Toggl clients for workspace $workspaceId" }
          "Could not load Toggl clients — check that your API key is valid."
        }
    model.addAttribute("clients", clients)
    model.addAttribute("clientsFetchError", error)
    model.addAttribute("togglFetchError", null)
    return "timer-index :: client-select"
  }

  private fun formErrorView(
      form: TimerForm,
      model: Model,
      hxRequest: Boolean,
      response: HttpServletResponse,
  ): String {
    model.addAttribute("message", "Welcome to the Timer Page!")
    model.addAttribute("formExpanded", true)
    loadDropdownData(form, model)
    loadHistoryData(model)
    loadTotalsData(model)
    addStoppedTimer(model)
    if (hxRequest) {
      response.setHeader("HX-Retarget", "#timer-form-card")
      response.setHeader("HX-Reswap", "outerHTML")
      return "timer-index :: timer-form"
    }
    return "timer-index"
  }

  private fun loadDropdownData(form: TimerForm, model: Model) {
    val workspacesResult = runCatching { togglService.fetchWorkspaces() }
    val workspaces: List<TogglWorkspace> = workspacesResult.getOrDefault(emptyList())
    val togglFetchError =
        workspacesResult.exceptionOrNull()?.let { ex ->
          logger.warn(ex) { "Failed to fetch Toggl workspaces" }
          "Could not load Toggl workspaces — check that your API key is valid."
        }

    val clients: List<TogglWorkspaceClient>
    val clientsFetchError: String?
    if (togglFetchError != null || form.workspaceId == null) {
      clients = emptyList()
      clientsFetchError = null
    } else {
      val clientsResult = runCatching { togglService.fetchClients(form.workspaceId) }
      clients = clientsResult.getOrDefault(emptyList())
      clientsFetchError =
          clientsResult.exceptionOrNull()?.let { ex ->
            logger.warn(ex) { "Failed to fetch Toggl clients for workspace ${form.workspaceId}" }
            "Could not load Toggl clients — check that your API key is valid."
          }
    }

    model.addAttribute("workspaces", workspaces)
    model.addAttribute("clients", clients)
    model.addAttribute("togglFetchError", togglFetchError)
    model.addAttribute("clientsFetchError", clientsFetchError)
  }

  private fun loadHistoryData(model: Model) {
    model.addAttribute("historyPage", timeEntryHistoryService.initialPage())
  }

  private fun loadTotalsData(model: Model) {
    model.addAttribute("timeTotals", timeEntryHistoryService.currentTotals())
  }

  private fun addRunningTimer(result: StartTimerResult, model: Model) {
    model.addAttribute("runningTimer", runningTimerView(result))
  }

  private fun addActualTimer(entry: TogglTimeEntry?, model: Model) {
    if (entry == null || entry.duration >= 0 || entry.id == null || entry.start == null) {
      addStoppedTimer(model)
    } else {
      addRunningTimer(entry.toStartTimerResult(), model)
    }
  }

  private fun addCurrentTimer(model: Model) {
    val running =
        runCatching { togglService.getCurrentRunningTimer() }
            .onFailure { logger.warn(it) { "Failed to refresh the current Toggl timer" } }
            .getOrNull()
    if (running == null) addStoppedTimer(model) else addRunningTimer(running, model)
  }

  private fun addStoppedTimer(model: Model) {
    val workspaceId = credentialsService.currentTogglWorkspaceId()
    val projects =
        workspaceId?.let { id ->
          runCatching { timeEntryProjectService.projectsForWorkspace(id) }
              .onFailure { logger.warn(it) { "Failed to load projects for stopped timer" } }
              .getOrDefault(emptyList())
        } ?: emptyList()
    model.addAttribute(
        "stoppedTimer",
        StoppedTimerView(workspaceId = workspaceId, projects = projects),
    )
  }

  private fun refreshCurrentTimerAfterSplit(
      model: Model,
      response: HttpServletResponse,
      notification: String?,
  ): String {
    val running =
        runCatching { togglService.getCurrentRunningTimer() }
            .onFailure { logger.warn(it) { "Failed to refresh current Toggl timer after split" } }
            .getOrNull()
    if (running == null) addStoppedTimer(model) else addRunningTimer(running, model)
    model.addAttribute("timerNotification", notification)
    response.setHeader("HX-Retarget", "#result")
    response.setHeader("HX-Reswap", "outerHTML")
    response.setHeader("HX-Trigger", "timeEntriesChanged, timeTotalsChanged")
    return "start-timer :: start-update-response"
  }

  private fun runningTimerView(result: StartTimerResult): RunningTimerView {
    val fallbackPicker =
        TimeEntryProjectPickerView(
            togglId = result.togglId,
            projectName = result.projectName,
            clientName = result.clientName,
            projectColor = result.projectColor,
        )
    val projectPicker =
        runCatching { timeEntryProjectService.currentPicker(result.togglId) }
            .onFailure {
              logger.warn(it) {
                "Failed to load project picker for running entry ${result.togglId}"
              }
            }
            .getOrNull()
            ?.let { picker ->
              picker.copy(
                  projectName = picker.projectName ?: fallbackPicker.projectName,
                  clientName = picker.clientName ?: fallbackPicker.clientName,
                  projectColor = picker.projectColor ?: fallbackPicker.projectColor,
              )
            } ?: fallbackPicker
    val splitStatus =
        runCatching { timeEntrySplitWorkflow.operationStatus(result.togglId) }.getOrNull()
    val splitDisabledReason =
        splitStatus?.message
            ?: RUNNING_SPLIT_TOO_SHORT_MESSAGE.takeIf {
              Duration.between(result.startTime, clock.instant()).seconds < 2
            }

    return RunningTimerView(
        startTime = result.startTime,
        descriptionEditor =
            TimeEntryDescriptionEditorView(
                togglId = result.togglId,
                description = result.description?.takeIf { it.isNotBlank() },
            ),
        projectPicker = projectPicker,
        startEditor = startEditorView(togglId = result.togglId, start = result.startTime),
        actions =
            TimeEntryActionsView(
                togglId = result.togglId,
                description = result.description?.takeIf { it.isNotBlank() },
                splitDisabledReason = splitDisabledReason,
                deleteDisabledReason = splitStatus?.message,
                splitStatus = splitStatus?.message,
                splitPolling = splitStatus?.pending == true,
                splitNeedsReview = splitStatus?.needsReview == true,
            ),
        splitEnableAt = result.startTime.plusSeconds(2).toEpochMilli(),
    )
  }

  private fun startEditorView(togglId: Long, start: Instant): TimeEntryStartEditorView {
    val zone = credentialsService.currentTimeZone()
    val localStart = start.atZone(zone)
    return TimeEntryStartEditorView(
        togglId = togglId,
        expectedStart = start,
        startDate = localStart.toLocalDate(),
        startTime = START_TIME_FORMATTER.format(localStart),
        today = LocalDate.now(clock.withZone(zone)),
        timeZone = zone.id,
    )
  }

  private fun TogglTimeEntry.toStartTimerResult(): StartTimerResult =
      StartTimerResult(
          togglId = requireNotNull(id) { "Updated Toggl entry is missing an id" },
          startTime = requireNotNull(start) { "Updated Toggl entry is missing a start" },
          projectName = projectName,
          description = description,
          clientName = clientName,
          projectColor = projectColor,
      )

  companion object {
    private val START_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private const val RUNNING_SPLIT_TOO_SHORT_MESSAGE =
        "The timer must run for at least two seconds before it can be split."
  }
}
