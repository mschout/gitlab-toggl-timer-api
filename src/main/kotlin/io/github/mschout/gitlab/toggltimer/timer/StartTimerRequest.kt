package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.gitlab.GitLabIssue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StartTimerRequest(
    @field:NotBlank val issueUrl: String,
    @field:NotNull val workspaceId: Long,
    @field:NotNull val clientId: Long,
    val start: Instant? = null,
    val description: String? = null,
) {
  internal fun issue(): GitLabIssue = GitLabIssue.fromUrl(issueUrl)
}
