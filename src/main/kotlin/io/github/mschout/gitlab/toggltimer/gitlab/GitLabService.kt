package io.github.mschout.gitlab.toggltimer.gitlab

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class GitLabService(private val gitLabClient: GitLabClient) {

  fun getGitlabIssueTitle(issue: GitLabIssue): String {
    val gitlabProject =
        gitLabClient.getProject(issue.groupName, issue.projectPath)
            ?: error("GitLab project not found: ${issue.groupName}/${issue.projectPath}")

    logger.info {
      "Found gitlab project for ${issue.groupName}/${issue.projectPath}: ${gitlabProject.id}"
    }

    val gitLabIssue =
        gitLabClient.getIssue(gitlabProject.id, issue.issueNumber)
            ?: error("GitLab issue not found: ${issue.issueNumber}")

    logger.info {
      "Found gitlab issue for ${issue.groupName}/${issue.projectPath}: ${gitLabIssue.title}"
    }

    return gitLabIssue.title
  }
}
