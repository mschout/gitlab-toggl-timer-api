package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.Instant
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/timer")
@Tag(name = "Timer")
class TimerController(private val timerService: TimerService) {

  @PostMapping("/start")
  @Operation(summary = "Start a timer for a GitLab issue")
  fun startTimer(@Validated @RequestBody startTimerRequest: StartTimerRequest): Instant =
      timerService.startTimer(startTimerRequest).startTime

  @PostMapping("/stop")
  @Operation(summary = "Stop the currently running Toggl timer")
  fun stopTimer(): StopTimerResult? = timerService.stopTimer()

  @PostMapping("/create-project")
  @Operation(summary = "Create a project in Toggl for a gitlab issue")
  fun createProject(
      @Validated @RequestBody createProjectRequest: CreateProjectRequest
  ): TogglProject = timerService.createProject(createProjectRequest)
}
