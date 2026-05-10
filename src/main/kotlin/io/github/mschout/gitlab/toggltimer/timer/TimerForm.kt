package io.github.mschout.gitlab.toggltimer.timer

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class TimerForm(
    @field:NotBlank val issueUrl: String = "",
    @field:NotNull val workspaceId: Long? = null,
    @field:NotNull val clientId: Long? = null,
    val description: String? = null,
) {
  fun toCreateProjectRequest(): CreateProjectRequest =
      CreateProjectRequest(
          issueUrl = issueUrl,
          workspaceId = requireNotNull(workspaceId),
          clientId = requireNotNull(clientId),
      )

  fun toStartTimerRequest(): StartTimerRequest =
      StartTimerRequest(
          issueUrl = issueUrl,
          workspaceId = requireNotNull(workspaceId),
          clientId = requireNotNull(clientId),
          description = description?.takeIf { it.isNotBlank() },
      )
}
