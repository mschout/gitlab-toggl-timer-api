package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.gitlab.GitLabIssue
import io.github.mschout.gitlab.toggltimer.gitlab.GitLabService
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class TimerServiceTest {

  private lateinit var gitLabService: GitLabService
  private lateinit var togglService: TogglService
  private lateinit var service: TimerService

  @BeforeEach
  fun setUp() {
    gitLabService = mock(GitLabService::class.java)
    togglService = mock(TogglService::class.java)
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

    `when`(gitLabService.getGitlabIssueTitle(expectedIssue)).thenReturn("Resolved title")
    `when`(togglService.findOrCreateProject(7L, 5L, 42L, "Resolved title")).thenReturn(project)
    `when`(togglService.startTimer(project, request)).thenReturn(timerStart)

    val result = service.startTimer(request)

    assertEquals(timerStart, result)
    verify(togglService).findOrCreateProject(7L, 5L, 42L, "Resolved title")
    verify(togglService).startTimer(project, request)
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

    `when`(gitLabService.getGitlabIssueTitle(expectedIssue)).thenReturn("Some issue")
    `when`(togglService.findOrCreateProject(11L, 22L, 99L, "Some issue")).thenReturn(project)

    val result = service.createProject(request)

    assertSame(project, result)
    verify(togglService).findOrCreateProject(11L, 22L, 99L, "Some issue")
  }

  @Test
  fun `startTimer should propagate gitlab service errors`() {
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/mygroup/myproject/-/issues/1",
            workspaceId = 7L,
            clientId = 5L,
        )
    `when`(gitLabService.getGitlabIssueTitle(GitLabIssue("mygroup", "myproject", 1L)))
        .thenThrow(IllegalStateException("GitLab project not found"))

    val ex =
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
          service.startTimer(request)
        }
    assertEquals("GitLab project not found", ex.message)
    org.mockito.Mockito.verifyNoInteractions(togglService)
  }

  @Test
  fun `createProject should propagate gitlab service errors`() {
    val request =
        CreateProjectRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/7",
            workspaceId = 1L,
            clientId = 2L,
        )
    `when`(gitLabService.getGitlabIssueTitle(GitLabIssue("g", "p", 7L)))
        .thenThrow(IllegalStateException("GitLab issue not found: 7"))

    org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
      service.createProject(request)
    }
    org.mockito.Mockito.verifyNoInteractions(togglService)
  }
}
