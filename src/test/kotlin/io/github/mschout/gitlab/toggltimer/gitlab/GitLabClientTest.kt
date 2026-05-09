package io.github.mschout.gitlab.toggltimer.gitlab

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.stream.Stream
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.IssuesApi
import org.gitlab4j.api.SearchApi
import org.gitlab4j.api.models.Issue
import org.gitlab4j.api.models.Project
import org.gitlab4j.models.Constants
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GitLabClientTest {

  private lateinit var gitLabApi: GitLabApi
  private lateinit var issuesApi: IssuesApi
  private lateinit var searchApi: SearchApi
  private lateinit var client: GitLabClient

  @BeforeEach
  fun setUp() {
    gitLabApi = mockk()
    issuesApi = mockk()
    searchApi = mockk()
    every { gitLabApi.issuesApi } returns issuesApi
    every { gitLabApi.searchApi } returns searchApi
    client = GitLabClient(gitLabApi)
  }

  @Test
  fun `getProject returns null when search returns no results`() {
    every {
      searchApi.groupSearchStream(
          "client-empty-grp",
          Constants.GroupSearchScope.PROJECTS,
          "client-empty-proj",
      )
    } returns Stream.empty()

    client.getProject("client-empty-grp", "client-empty-proj").shouldBeNull()
  }

  @Test
  fun `getProject returns null when no result matches the requested path`() {
    val mismatched =
        Project().apply {
          id = 1L
          path = "client-actually-different"
        }
    every {
      searchApi.groupSearchStream(
          "client-mismatch-grp",
          Constants.GroupSearchScope.PROJECTS,
          "client-mismatch-proj",
      )
    } returns Stream.of(mismatched)

    client.getProject("client-mismatch-grp", "client-mismatch-proj").shouldBeNull()
  }

  @Test
  fun `getProject selects the project whose path matches when multiple results returned`() {
    val mismatch =
        Project().apply {
          id = 1L
          path = "client-other"
        }
    val match =
        Project().apply {
          id = 2L
          path = "client-multi-proj"
        }
    every {
      searchApi.groupSearchStream(
          "client-multi-grp",
          Constants.GroupSearchScope.PROJECTS,
          "client-multi-proj",
      )
    } returns Stream.of(mismatch, match)

    val result = client.getProject("client-multi-grp", "client-multi-proj")

    result?.id shouldBe 2L
  }

  @Test
  fun `getIssue returns the issue from the API`() {
    val issue = Issue().apply { title = "Hello client" }
    every { issuesApi.getIssue(101L, 202L) } returns issue

    val result = client.getIssue(101L, 202L)

    result?.title shouldBe "Hello client"
  }

  @Test
  fun `getIssue returns null when API returns null`() {
    every { issuesApi.getIssue(303L, 404L) } returns null

    client.getIssue(303L, 404L).shouldBeNull()
  }
}
