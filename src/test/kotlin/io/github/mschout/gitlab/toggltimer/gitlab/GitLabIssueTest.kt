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
package io.github.mschout.gitlab.toggltimer.gitlab

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GitLabIssueTest {

  @Test
  fun `should parse standard gitlab issue url`() {
    val issue = GitLabIssue.fromUrl("https://gitlab.com/mygroup/myproject/-/work_items/123")

    assertSoftly(issue) {
      groupName shouldBe "mygroup"
      projectPath shouldBe "myproject"
      issueNumber shouldBe 123L
    }
  }

  @Test
  fun `should parse self-hosted gitlab url with custom port`() {
    val issue = GitLabIssue.fromUrl("https://gitlab.example.com:8443/team/repo/-/work_items/7")

    assertSoftly(issue) {
      groupName shouldBe "team"
      projectPath shouldBe "repo"
      issueNumber shouldBe 7L
    }
  }

  @Test
  fun `should parse url with query string and fragment`() {
    val issue =
        GitLabIssue.fromUrl(
            "https://gitlab.com/mygroup/myproject/-/work_items/42?note=1#discussion_42"
        )

    assertSoftly(issue) {
      groupName shouldBe "mygroup"
      projectPath shouldBe "myproject"
      issueNumber shouldBe 42L
    }
  }

  @Test
  fun `should parse url with trailing slash`() {
    val issue = GitLabIssue.fromUrl("https://gitlab.com/mygroup/myproject/-/work_items/99/")

    assertSoftly(issue) {
      groupName shouldBe "mygroup"
      projectPath shouldBe "myproject"
      issueNumber shouldBe 99L
    }
  }

  @Test
  fun `should reject url with too few path segments`() {
    val ex =
        shouldThrow<GitLabIssueNotFoundException> {
          GitLabIssue.fromUrl("https://gitlab.com/group/project")
        }
    ex.message shouldBe "GitLab issue not found for URL: https://gitlab.com/group/project"
  }

  @Test
  fun `should reject root url`() {
    shouldThrow<GitLabIssueNotFoundException> { GitLabIssue.fromUrl("https://gitlab.com/") }
  }

  @Test
  fun `should reject url with non-numeric issue number`() {
    shouldThrow<GitLabIssueNotFoundException> {
      GitLabIssue.fromUrl("https://gitlab.com/group/project/-/work_items/abc")
    }
  }

  @Test
  fun `should reject merge request url as an issue not found`() {
    val url = "https://gitlab.com/group/project/-/merge_requests/42"

    val ex = shouldThrow<GitLabIssueNotFoundException> { GitLabIssue.fromUrl(url) }

    ex.message shouldBe "GitLab issue not found for URL: $url"
  }

  @Test
  fun `should reject legacy issues url as an issue not found`() {
    val url = "https://gitlab.com/group/project/-/issues/42"

    val ex = shouldThrow<GitLabIssueNotFoundException> { GitLabIssue.fromUrl(url) }

    ex.message shouldBe "GitLab issue not found for URL: $url"
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
