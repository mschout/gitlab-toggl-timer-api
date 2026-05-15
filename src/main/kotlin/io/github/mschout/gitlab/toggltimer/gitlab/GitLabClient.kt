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
