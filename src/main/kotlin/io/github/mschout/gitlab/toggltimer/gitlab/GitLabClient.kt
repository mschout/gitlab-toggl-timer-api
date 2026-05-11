package io.github.mschout.gitlab.toggltimer.gitlab

import io.github.mschout.gitlab.toggltimer.configuration.CacheManagerConfiguration
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gitlab4j.api.models.Issue
import org.gitlab4j.api.models.Project
import org.gitlab4j.models.Constants
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class GitLabClient(
    private val gitLabApiFactory: GitLabApiFactory,
    private val credentialsService: CurrentUserCredentialsService,
) {

  fun currentUserId(): Long = credentialsService.currentUserId()

  @Cacheable(
      value = [CacheManagerConfiguration.GITLAB_ISSUE_CACHE],
      key = "#root.target.currentUserId() + ':' + #projectId + ':' + #issueNumber",
  )
  fun getIssue(projectId: Long, issueNumber: Long): Issue? {
    logger.info { "Looking up project $projectId issue $issueNumber using GitLab API" }
    return gitLabApi().issuesApi.getIssue(projectId, issueNumber)
  }

  @Cacheable(
      value = [CacheManagerConfiguration.GITLAB_PROJECT_CACHE],
      key = "#root.target.currentUserId() + ':' + #groupName + '/' + #projectPath",
  )
  fun getProject(groupName: String, projectPath: String): Project? {
    logger.info { "Looking up project $groupName/$projectPath using GitLab API" }
    return gitLabApi()
        .searchApi
        .groupSearchStream(groupName, Constants.GroupSearchScope.PROJECTS, projectPath)
        .filter { it.javaClass == Project::class.java }
        .map { it as Project }
        .filter { it.path == projectPath }
        .findFirst()
        .orElse(null)
  }

  private fun gitLabApi() = gitLabApiFactory.forToken(credentialsService.requireGitlabToken())
}
