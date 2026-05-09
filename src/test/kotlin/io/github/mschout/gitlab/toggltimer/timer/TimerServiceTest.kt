package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.gitlab.GitLabIssue
import io.github.mschout.gitlab.toggltimer.gitlab.GitLabService
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
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

    every { gitLabService.getGitlabIssueTitle(expectedIssue) } returns "Resolved title"
    every { togglService.findOrCreateProject(7L, 5L, 42L, "Resolved title") } returns project
    every { togglService.startTimer(project, request) } returns timerStart

    val result = service.startTimer(request)

    assertEquals(timerStart, result)
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

    assertSame(project, result)
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

    val ex = assertThrows(IllegalStateException::class.java) { service.startTimer(request) }
    assertEquals("GitLab project not found", ex.message)
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

    assertThrows(IllegalStateException::class.java) { service.createProject(request) }
    verify { togglService wasNot Called }
  }
}
