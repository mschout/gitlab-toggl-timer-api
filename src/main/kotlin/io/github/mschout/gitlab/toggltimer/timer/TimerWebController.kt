package io.github.mschout.gitlab.toggltimer.timer

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

@Controller
@RequestMapping("/timer")
class TimerWebController(private val timerService: TimerService) {

  @GetMapping
  fun index(model: Model): String {
    if (!model.containsAttribute("form")) {
      model.addAttribute("form", TimerForm())
    }
    model.addAttribute("message", "Welcome to the Timer Page!")
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
      return formErrorView(model, hxRequest, response)
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
    val start = timerService.startTimer(request)
    return ModelAndView("start-timer").apply { addObject("startTime", start) }
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
      return formErrorView(model, hxRequest, response)
    }
    val start = timerService.startTimer(form.toStartTimerRequest())
    model.addAttribute("startTime", start)
    return if (hxRequest) "start-timer :: result-card" else "start-timer"
  }

  private fun formErrorView(
      model: Model,
      hxRequest: Boolean,
      response: HttpServletResponse,
  ): String {
    model.addAttribute("message", "Welcome to the Timer Page!")
    if (hxRequest) {
      response.setHeader("HX-Retarget", "#timer-form-card")
      response.setHeader("HX-Reswap", "outerHTML")
      return "timer-index :: timer-form"
    }
    return "timer-index"
  }
}
