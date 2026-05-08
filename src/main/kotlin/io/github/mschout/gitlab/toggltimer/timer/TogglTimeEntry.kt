package io.github.mschout.gitlab.toggltimer.timer

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TogglTimeEntry(
    val workspaceId: Long,
    val projectId: Long,
    val start: Instant,
    val description: String? = null,
    val duration: Long = -1L,
    val createdWith: String = "Gitlab Toggl Timer",
)
