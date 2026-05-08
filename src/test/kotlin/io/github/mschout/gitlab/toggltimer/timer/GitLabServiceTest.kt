package io.github.mschout.gitlab.toggltimer.timer

import java.util.stream.Stream
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.IssuesApi
import org.gitlab4j.api.SearchApi
import org.gitlab4j.api.models.Issue
import org.gitlab4j.api.models.Project
import org.gitlab4j.models.Constants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * The production methods on [GitLabService] are `@Cacheable` with AspectJ post-compile weaving, so
 * results are cached at the JVM level once any other Spring Boot test in this run has initialized
 * the CacheManager. Each test uses a unique (groupName, projectPath, issueNumber) tuple to avoid
 * collisions between cached entries from sibling tests.
 */
class GitLabServiceTest {

  private lateinit var gitLabApi: GitLabApi
  private lateinit var issuesApi: IssuesApi
  private lateinit var searchApi: SearchApi
  private lateinit var service: GitLabService

  @BeforeEach
  fun setUp() {
    gitLabApi = mock(GitLabApi::class.java)
    issuesApi = mock(IssuesApi::class.java)
    searchApi = mock(SearchApi::class.java)
    `when`(gitLabApi.issuesApi).thenReturn(issuesApi)
    `when`(gitLabApi.searchApi).thenReturn(searchApi)
    service = GitLabService(gitLabApi)
  }

  @Test
  fun `should return issue title when project and issue are found`() {
    val project =
        Project().apply {
          id = 7L
          path = "found-proj"
        }
    val issue = Issue().apply { title = "Hello world" }
    `when`(
            searchApi.groupSearchStream(
                "found-grp",
                Constants.GroupSearchScope.PROJECTS,
                "found-proj",
            )
        )
        .thenReturn(Stream.of(project))
    `when`(issuesApi.getIssue(7L, 42L)).thenReturn(issue)

    val result = service.getGitlabIssueTitle(GitLabIssue("found-grp", "found-proj", 42L))

    assertEquals("Hello world", result)
  }

  @Test
  fun `should throw when search returns no projects`() {
    `when`(
            searchApi.groupSearchStream(
                "empty-grp",
                Constants.GroupSearchScope.PROJECTS,
                "empty-proj",
            )
        )
        .thenReturn(Stream.empty())

    val ex =
        assertThrows(IllegalStateException::class.java) {
          service.getGitlabIssueTitle(GitLabIssue("empty-grp", "empty-proj", 1L))
        }
    assertEquals("GitLab project not found: empty-grp/empty-proj", ex.message)
  }

  @Test
  fun `should throw when no project matches the requested path`() {
    val mismatched =
        Project().apply {
          id = 1L
          path = "actually-different"
        }
    `when`(
            searchApi.groupSearchStream(
                "mismatch-grp",
                Constants.GroupSearchScope.PROJECTS,
                "mismatch-proj",
            )
        )
        .thenReturn(Stream.of(mismatched))

    val ex =
        assertThrows(IllegalStateException::class.java) {
          service.getGitlabIssueTitle(GitLabIssue("mismatch-grp", "mismatch-proj", 1L))
        }
    assertEquals("GitLab project not found: mismatch-grp/mismatch-proj", ex.message)
  }

  @Test
  fun `should select the project whose path matches when multiple results returned`() {
    val mismatch =
        Project().apply {
          id = 1L
          path = "other"
        }
    val match =
        Project().apply {
          id = 2L
          path = "multi-proj"
        }
    `when`(
            searchApi.groupSearchStream(
                "multi-grp",
                Constants.GroupSearchScope.PROJECTS,
                "multi-proj",
            )
        )
        .thenReturn(Stream.of(mismatch, match))
    `when`(issuesApi.getIssue(2L, 99L)).thenReturn(Issue().apply { title = "Found" })

    val result = service.getGitlabIssueTitle(GitLabIssue("multi-grp", "multi-proj", 99L))

    assertEquals("Found", result)
  }

  @Test
  fun `should throw when issue lookup returns null`() {
    val project =
        Project().apply {
          id = 9L
          path = "noissue-proj"
        }
    `when`(
            searchApi.groupSearchStream(
                "noissue-grp",
                Constants.GroupSearchScope.PROJECTS,
                "noissue-proj",
            )
        )
        .thenReturn(Stream.of(project))
    `when`(issuesApi.getIssue(9L, 42L)).thenReturn(null)

    val ex =
        assertThrows(IllegalStateException::class.java) {
          service.getGitlabIssueTitle(GitLabIssue("noissue-grp", "noissue-proj", 42L))
        }
    assertEquals("GitLab issue not found: 42", ex.message)
  }
}
