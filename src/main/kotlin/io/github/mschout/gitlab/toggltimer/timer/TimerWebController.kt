package io.github.mschout.gitlab.toggltimer.timer

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("/timer")
class TimerWebController(private val timerService: TimerService) {

  @GetMapping
  fun index(): ModelAndView =
      ModelAndView("timer-index").apply { addObject("message", "Welcome to the Timer Page!") }

  @GetMapping("/create-project")
  fun createProject(
      @RequestParam issueUrl: String,
      @RequestParam workspaceId: Long,
      @RequestParam clientId: Long,
  ): ModelAndView {
    val project = timerService.createProject(CreateProjectRequest(issueUrl, workspaceId, clientId))
    return ModelAndView("create-project").apply { addObject("project", project) }
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
}
