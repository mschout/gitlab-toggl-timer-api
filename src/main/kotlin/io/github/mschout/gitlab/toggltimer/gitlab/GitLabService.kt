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
