package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspaceClient
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
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
) {

  @GetMapping
  fun index(model: Model): String {
    val form =
        model.getAttribute("form") as? TimerForm
            ?: TimerForm(workspaceId = credentialsService.currentTogglWorkspaceId()).also {
              model.addAttribute("form", it)
            }
    model.addAttribute("message", "Welcome to the Timer Page!")
    loadDropdownData(form, model)

    val running =
        runCatching { togglService.getCurrentRunningTimer() }
            .onFailure { logger.warn(it) { "Failed to fetch current Toggl timer" } }
            .getOrNull()
    if (running != null) {
      model.addAttribute("startTime", running.startTime)
      model.addAttribute("projectName", running.projectName)
      model.addAttribute("description", running.description)
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
      addObject("startTime", result.startTime)
      addObject("projectName", result.projectName)
      addObject("description", result.description)
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
    if (bindingResult.hasErrors()) {
      return formErrorView(form, model, hxRequest, response)
    }
    val result = timerService.startTimer(form.toStartTimerRequest())
    model.addAttribute("startTime", result.startTime)
    model.addAttribute("projectName", result.projectName)
    model.addAttribute("description", result.description)
    return if (hxRequest) "start-timer :: result-card" else "start-timer"
  }

  @PostMapping("/stop")
  fun stopTimerSubmit(
      @RequestHeader(name = "HX-Request", required = false) hxRequest: Boolean = false,
      model: Model,
  ): String {
    val result = timerService.stopTimer()
    if (result != null) {
      model.addAttribute("durationFormatted", result.durationFormatted)
      model.addAttribute("stopped", true)
    } else {
      model.addAttribute("stopped", false)
    }
    return if (hxRequest) "stop-timer :: result-card" else "stop-timer"
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
    loadDropdownData(form, model)
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
}
