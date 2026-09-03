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

import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspaceClient
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import java.time.LocalDate
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
    private val timeEntryProjectService: TimeEntryProjectService,
    private val timeEntryDeletionService: TimeEntryDeletionService,
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
    val project = timerService.createProject(form.toCreateProjectRequest())
    model.addAttribute("project", project)
    return if (hxRequest) "create-project :: result-card" else "create-project"
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
    val result = timerService.startTimer(form.toStartTimerRequest())
    addRunningTimer(result, model)
    loadTotalsData(model)
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

    return RunningTimerView(
        startTime = result.startTime,
        descriptionEditor =
            TimeEntryDescriptionEditorView(
                togglId = result.togglId,
                description = result.description?.takeIf { it.isNotBlank() },
            ),
        projectPicker = projectPicker,
    )
  }
}
