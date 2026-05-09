package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.gitlab.GitLabIssue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GitLabIssueTest {

  @Test
  fun `should parse standard gitlab issue url`() {
    val issue = GitLabIssue.fromUrl("https://gitlab.com/mygroup/myproject/-/issues/123")

    assertEquals("mygroup", issue.groupName)
    assertEquals("myproject", issue.projectPath)
    assertEquals(123L, issue.issueNumber)
  }

  @Test
  fun `should parse self-hosted gitlab url with custom port`() {
    val issue = GitLabIssue.fromUrl("https://gitlab.example.com:8443/team/repo/-/issues/7")

    assertEquals("team", issue.groupName)
    assertEquals("repo", issue.projectPath)
    assertEquals(7L, issue.issueNumber)
  }

  @Test
  fun `should parse url with query string and fragment`() {
    val issue =
        GitLabIssue.fromUrl("https://gitlab.com/mygroup/myproject/-/issues/42?note=1#discussion_42")

    assertEquals("mygroup", issue.groupName)
    assertEquals("myproject", issue.projectPath)
    assertEquals(42L, issue.issueNumber)
  }

  @Test
  fun `should parse url with trailing slash`() {
    val issue = GitLabIssue.fromUrl("https://gitlab.com/mygroup/myproject/-/issues/99/")

    assertEquals("mygroup", issue.groupName)
    assertEquals("myproject", issue.projectPath)
    assertEquals(99L, issue.issueNumber)
  }

  @Test
  fun `should reject url with too few path segments`() {
    val ex =
        assertThrows(IllegalArgumentException::class.java) {
          GitLabIssue.fromUrl("https://gitlab.com/group/project")
        }
    assertEquals("Invalid GitLab issue URL: https://gitlab.com/group/project", ex.message)
  }

  @Test
  fun `should reject root url`() {
    assertThrows(IllegalArgumentException::class.java) {
      GitLabIssue.fromUrl("https://gitlab.com/")
    }
  }

  @Test
  fun `should reject url with non-numeric issue number`() {
    assertThrows(NumberFormatException::class.java) {
      GitLabIssue.fromUrl("https://gitlab.com/group/project/-/issues/abc")
    }
  }

  @Test
  fun `should preserve constructor values via data class`() {
    val a = GitLabIssue("g", "p", 1L)
    val b = GitLabIssue("g", "p", 1L)
    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
  }
}
