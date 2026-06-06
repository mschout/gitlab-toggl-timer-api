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
package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.gitlab.GitLabIssue
import jakarta.validation.constraints.NotNull
import java.time.Instant
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StartTimerRequest(
    val issueUrl: String? = null,
    @field:NotNull val workspaceId: Long,
    val clientId: Long? = null,
    val start: Instant? = null,
    val description: String? = null,
) {
  /**
   * Parses [issueUrl] into a [GitLabIssue], or returns null when no issue URL was provided. A null
   * result means the timer should start without an associated Toggl project.
   */
  internal fun issue(): GitLabIssue? =
      issueUrl?.takeIf { it.isNotBlank() }?.let { GitLabIssue.fromUrl(it) }
}
