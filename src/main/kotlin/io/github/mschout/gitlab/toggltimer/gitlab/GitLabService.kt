package io.github.mschout.gitlab.toggltimer.gitlab

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GitLabService(private val gitLabClient: GitLabClient) {

  fun getGitlabIssueTitle(issue: GitLabIssue): String {
    val gitlabProject =
        gitLabClient.getProject(issue.groupName, issue.projectPath)
            ?: error("GitLab project not found: ${issue.groupName}/${issue.projectPath}")

    log.info(
        "Found gitlab project for {}/{}: {}",
        issue.groupName,
        issue.projectPath,
        gitlabProject.id,
    )

    val gitLabIssue =
        gitLabClient.getIssue(gitlabProject.id, issue.issueNumber)
            ?: error("GitLab issue not found: ${issue.issueNumber}")

    log.info(
        "Found gitlab issue for {}/{}: {}",
        issue.groupName,
        issue.projectPath,
        gitLabIssue.title,
    )

    return gitLabIssue.title
  }

  companion object {
    private val log = LoggerFactory.getLogger(GitLabService::class.java)
  }
}
