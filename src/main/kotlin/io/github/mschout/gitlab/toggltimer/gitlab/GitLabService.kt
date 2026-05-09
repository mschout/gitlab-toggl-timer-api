package io.github.mschout.gitlab.toggltimer.gitlab

import io.github.mschout.gitlab.toggltimer.configuration.CacheManagerConfiguration
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.models.Issue
import org.gitlab4j.api.models.Project
import org.gitlab4j.models.Constants
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class GitLabService(private val gitLabApi: GitLabApi) {

  fun getGitlabIssueTitle(issue: GitLabIssue): String {
    val gitlabProject =
        getProject(issue.groupName, issue.projectPath)
            ?: error("GitLab project not found: ${issue.groupName}/${issue.projectPath}")

    log.info(
        "Found gitlab project for {}/{}: {}",
        issue.groupName,
        issue.projectPath,
        gitlabProject.id,
    )

    val gitLabIssue =
        getGitlabProjectIssue(gitlabProject.id, issue.issueNumber)
            ?: error("GitLab issue not found: ${issue.issueNumber}")

    log.info(
        "Found gitlab issue for {}/{}: {}",
        issue.groupName,
        issue.projectPath,
        gitLabIssue.title,
    )

    return gitLabIssue.title
  }

  @Cacheable(CacheManagerConfiguration.GITLAB_ISSUE_CACHE)
  protected fun getGitlabProjectIssue(projectId: Long, issueNumber: Long): Issue? {
    log.info("Looking up project {} issue {} using GitLab API", projectId, issueNumber)
    return gitLabApi.issuesApi.getIssue(projectId, issueNumber)
  }

  @Cacheable(CacheManagerConfiguration.GITLAB_PROJECT_CACHE)
  protected fun getProject(groupName: String, projectPath: String): Project? {
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
    private val log = LoggerFactory.getLogger(GitLabService::class.java)
  }
}
