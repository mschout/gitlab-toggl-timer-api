package io.github.mschout.gitlab.toggltimer.gitlab

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GitLabIssueTest {

  @Test
  fun `should parse standard gitlab issue url`() {
    val issue = GitLabIssue.fromUrl("https://gitlab.com/mygroup/myproject/-/issues/123")

    assertSoftly(issue) {
      groupName shouldBe "mygroup"
      projectPath shouldBe "myproject"
      issueNumber shouldBe 123L
    }
  }

  @Test
  fun `should parse self-hosted gitlab url with custom port`() {
    val issue = GitLabIssue.fromUrl("https://gitlab.example.com:8443/team/repo/-/issues/7")

    assertSoftly(issue) {
      groupName shouldBe "team"
      projectPath shouldBe "repo"
      issueNumber shouldBe 7L
    }
  }

  @Test
  fun `should parse url with query string and fragment`() {
    val issue =
        GitLabIssue.fromUrl("https://gitlab.com/mygroup/myproject/-/issues/42?note=1#discussion_42")

    assertSoftly(issue) {
      groupName shouldBe "mygroup"
      projectPath shouldBe "myproject"
      issueNumber shouldBe 42L
    }
  }

  @Test
  fun `should parse url with trailing slash`() {
    val issue = GitLabIssue.fromUrl("https://gitlab.com/mygroup/myproject/-/issues/99/")

    assertSoftly(issue) {
      groupName shouldBe "mygroup"
      projectPath shouldBe "myproject"
      issueNumber shouldBe 99L
    }
  }

  @Test
  fun `should reject url with too few path segments`() {
    val ex =
        shouldThrow<IllegalArgumentException> {
          GitLabIssue.fromUrl("https://gitlab.com/group/project")
        }
    ex.message shouldBe "Invalid GitLab issue URL: https://gitlab.com/group/project"
  }

  @Test
  fun `should reject root url`() {
    shouldThrow<IllegalArgumentException> { GitLabIssue.fromUrl("https://gitlab.com/") }
  }

  @Test
  fun `should reject url with non-numeric issue number`() {
    shouldThrow<NumberFormatException> {
      GitLabIssue.fromUrl("https://gitlab.com/group/project/-/issues/abc")
    }
  }

  @Test
  fun `should preserve constructor values via data class`() {
    val a = GitLabIssue("g", "p", 1L)
    val b = GitLabIssue("g", "p", 1L)
    assertSoftly {
      a shouldBe b
      a.hashCode() shouldBe b.hashCode()
    }
  }
}
