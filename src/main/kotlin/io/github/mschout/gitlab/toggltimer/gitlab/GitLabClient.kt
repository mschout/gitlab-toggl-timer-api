package io.github.mschout.gitlab.toggltimer.gitlab

import io.github.mschout.gitlab.toggltimer.configuration.CacheManagerConfiguration
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.models.Issue
import org.gitlab4j.api.models.Project
import org.gitlab4j.models.Constants
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@Component
class GitLabClient(private val gitLabApi: GitLabApi) {

  @Cacheable(CacheManagerConfiguration.GITLAB_ISSUE_CACHE)
  fun getIssue(projectId: Long, issueNumber: Long): Issue? {
    log.info("Looking up project {} issue {} using GitLab API", projectId, issueNumber)
    return gitLabApi.issuesApi.getIssue(projectId, issueNumber)
  }

  @Cacheable(CacheManagerConfiguration.GITLAB_PROJECT_CACHE)
  fun getProject(groupName: String, projectPath: String): Project? {
    log.info("Looking up project {}/{} using GitLab API", groupName, projectPath)
    return gitLabApi.searchApi
        .groupSearchStream(groupName, Constants.GroupSearchScope.PROJECTS, projectPath)
        .filter { it.javaClass == Project::class.java }
        .map { it as Project }
        .filter { it.path == projectPath }
        .findFirst()
        .orElse(null)
  }

  companion object {
    private val log = LoggerFactory.getLogger(GitLabClient::class.java)
  }
}
