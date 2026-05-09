package io.github.mschout.gitlab.toggltimer.gitlab

import java.util.stream.Stream
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.IssuesApi
import org.gitlab4j.api.SearchApi
import org.gitlab4j.api.models.Issue
import org.gitlab4j.api.models.Project
import org.gitlab4j.models.Constants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class GitLabClientTest {

  private lateinit var gitLabApi: GitLabApi
  private lateinit var issuesApi: IssuesApi
  private lateinit var searchApi: SearchApi
  private lateinit var client: GitLabClient

  @BeforeEach
  fun setUp() {
    gitLabApi = mock(GitLabApi::class.java)
    issuesApi = mock(IssuesApi::class.java)
    searchApi = mock(SearchApi::class.java)
    `when`(gitLabApi.issuesApi).thenReturn(issuesApi)
    `when`(gitLabApi.searchApi).thenReturn(searchApi)
    client = GitLabClient(gitLabApi)
  }

  @Test
  fun `getProject returns null when search returns no results`() {
    `when`(
            searchApi.groupSearchStream(
                "client-empty-grp",
                Constants.GroupSearchScope.PROJECTS,
                "client-empty-proj",
            )
        )
        .thenReturn(Stream.empty())

    assertNull(client.getProject("client-empty-grp", "client-empty-proj"))
  }

  @Test
  fun `getProject returns null when no result matches the requested path`() {
    val mismatched =
        Project().apply {
          id = 1L
          path = "client-actually-different"
        }
    `when`(
            searchApi.groupSearchStream(
                "client-mismatch-grp",
                Constants.GroupSearchScope.PROJECTS,
                "client-mismatch-proj",
            )
        )
        .thenReturn(Stream.of(mismatched))

    assertNull(client.getProject("client-mismatch-grp", "client-mismatch-proj"))
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
    `when`(
            searchApi.groupSearchStream(
                "client-multi-grp",
                Constants.GroupSearchScope.PROJECTS,
                "client-multi-proj",
            )
        )
        .thenReturn(Stream.of(mismatch, match))

    val result = client.getProject("client-multi-grp", "client-multi-proj")

    assertEquals(2L, result?.id)
  }

  @Test
  fun `getIssue returns the issue from the API`() {
    val issue = Issue().apply { title = "Hello client" }
    `when`(issuesApi.getIssue(101L, 202L)).thenReturn(issue)

    val result = client.getIssue(101L, 202L)

    assertEquals("Hello client", result?.title)
  }

  @Test
  fun `getIssue returns null when API returns null`() {
    `when`(issuesApi.getIssue(303L, 404L)).thenReturn(null)

    assertNull(client.getIssue(303L, 404L))
  }
}
