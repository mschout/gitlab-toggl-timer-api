package io.github.mschout.gitlab.toggltimer.toggl

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TogglTimeEntry(
    val workspaceId: Long? = null,
    val projectId: Long? = null,
    val start: Instant? = null,
    val description: String? = null,
    val duration: Long = -1L,
    val createdWith: String? = null,
    val id: Long? = null,
)
