package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.gitlab.GitLabIssue
import jakarta.validation.constraints.NotNull

data class CreateProjectRequest(
    @field:NotNull val issueUrl: String,
    @field:NotNull val workspaceId: Long,
    @field:NotNull val clientId: Long,
) {
  internal fun issue(): GitLabIssue = GitLabIssue.fromUrl(issueUrl)
}
