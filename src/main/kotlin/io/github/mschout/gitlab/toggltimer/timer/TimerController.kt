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

import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.Instant
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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

  @PostMapping("/sync-history")
  @Operation(summary = "Backfill Toggl time entries from the past N days into Postgres")
  fun syncHistory(@RequestParam(defaultValue = "90") days: Int): SyncHistoryResult =
      timerService.syncHistory(days)

  @PostMapping("/sync-projects")
  @Operation(summary = "Backfill Toggl projects across all workspaces into Postgres")
  fun syncProjects(): SyncProjectsResult = timerService.syncProjects()
}
