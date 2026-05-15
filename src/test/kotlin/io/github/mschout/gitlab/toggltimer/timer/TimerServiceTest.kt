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

import io.github.mschout.gitlab.toggltimer.gitlab.GitLabIssue
import io.github.mschout.gitlab.toggltimer.gitlab.GitLabService
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimerServiceTest {

  private lateinit var gitLabService: GitLabService
  private lateinit var togglService: TogglService
  private lateinit var service: TimerService

  @BeforeEach
  fun setUp() {
    gitLabService = mockk()
    togglService = mockk()
    service = TimerService(gitLabService, togglService)
  }

  @Test
  fun `startTimer should resolve issue title and start toggl timer for resolved project`() {
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/mygroup/myproject/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
        )
    val expectedIssue = GitLabIssue("mygroup", "myproject", 42L)
    val project = TogglProject(id = 100L, name = "42 - Resolved title", clientId = 5L)
    val timerStart = Instant.parse("2026-05-08T15:00:00Z")
    val timerResult =
        StartTimerResult(
            startTime = timerStart,
            projectName = "42 - Resolved title",
            description = "tracking",
        )

    every { gitLabService.getGitlabIssueTitle(expectedIssue) } returns "Resolved title"
    every { togglService.findOrCreateProject(7L, 5L, 42L, "Resolved title") } returns project
    every { togglService.startTimer(project, request) } returns timerResult

    val result = service.startTimer(request)

    result shouldBe timerResult
    result.startTime shouldBe timerStart
    result.projectName shouldBe "42 - Resolved title"
    result.description shouldBe "tracking"
    verify { togglService.findOrCreateProject(7L, 5L, 42L, "Resolved title") }
    verify { togglService.startTimer(project, request) }
  }

  @Test
  fun `createProject should resolve issue title and delegate to toggl find or create`() {
    val request =
        CreateProjectRequest(
            issueUrl = "https://gitlab.com/teamA/repoA/-/issues/99",
            workspaceId = 11L,
            clientId = 22L,
        )
    val expectedIssue = GitLabIssue("teamA", "repoA", 99L)
    val project = TogglProject(id = 200L, name = "99 - Some issue", clientId = 22L)

    every { gitLabService.getGitlabIssueTitle(expectedIssue) } returns "Some issue"
    every { togglService.findOrCreateProject(11L, 22L, 99L, "Some issue") } returns project

    val result = service.createProject(request)

    result shouldBeSameInstanceAs project
    verify { togglService.findOrCreateProject(11L, 22L, 99L, "Some issue") }
  }

  @Test
  fun `startTimer should propagate gitlab service errors`() {
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/mygroup/myproject/-/issues/1",
            workspaceId = 7L,
            clientId = 5L,
        )
    every { gitLabService.getGitlabIssueTitle(GitLabIssue("mygroup", "myproject", 1L)) } throws
        IllegalStateException("GitLab project not found")

    val ex = shouldThrow<IllegalStateException> { service.startTimer(request) }
    ex.message shouldBe "GitLab project not found"
    verify { togglService wasNot Called }
  }

  @Test
  fun `createProject should propagate gitlab service errors`() {
    val request =
        CreateProjectRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/7",
            workspaceId = 1L,
            clientId = 2L,
        )
    every { gitLabService.getGitlabIssueTitle(GitLabIssue("g", "p", 7L)) } throws
        IllegalStateException("GitLab issue not found: 7")

    shouldThrow<IllegalStateException> { service.createProject(request) }
    verify { togglService wasNot Called }
  }
}
