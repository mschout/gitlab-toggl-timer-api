package io.github.mschout.gitlab.toggltimer.gitlab

import io.mockk.every
import io.mockk.mockk
import org.gitlab4j.api.models.Issue
import org.gitlab4j.api.models.Project
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GitLabServiceTest {

  private lateinit var gitLabClient: GitLabClient
  private lateinit var service: GitLabService

  @BeforeEach
  fun setUp() {
    gitLabClient = mockk()
    service = GitLabService(gitLabClient)
  }

  @Test
  fun `should return issue title when project and issue are found`() {
    val project =
        Project().apply {
          id = 7L
          path = "found-proj"
        }
    val issue = Issue().apply { title = "Hello world" }
    every { gitLabClient.getProject("found-grp", "found-proj") } returns project
    every { gitLabClient.getIssue(7L, 42L) } returns issue

    val result = service.getGitlabIssueTitle(GitLabIssue("found-grp", "found-proj", 42L))

    assertEquals("Hello world", result)
  }

  @Test
  fun `should throw when client returns no project`() {
    every { gitLabClient.getProject("missing-grp", "missing-proj") } returns null

    val ex =
        assertThrows(IllegalStateException::class.java) {
          service.getGitlabIssueTitle(GitLabIssue("missing-grp", "missing-proj", 1L))
        }
    assertEquals("GitLab project not found: missing-grp/missing-proj", ex.message)
  }

  @Test
  fun `should throw when client returns no issue`() {
    val project =
        Project().apply {
          id = 9L
          path = "noissue-proj"
        }
    every { gitLabClient.getProject("noissue-grp", "noissue-proj") } returns project
    every { gitLabClient.getIssue(9L, 42L) } returns null

    val ex =
        assertThrows(IllegalStateException::class.java) {
          service.getGitlabIssueTitle(GitLabIssue("noissue-grp", "noissue-proj", 42L))
        }
    assertEquals("GitLab issue not found: 42", ex.message)
  }
}
