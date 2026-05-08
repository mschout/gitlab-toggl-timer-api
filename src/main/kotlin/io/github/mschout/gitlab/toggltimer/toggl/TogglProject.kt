package io.github.mschout.gitlab.toggltimer.toggl

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class TogglProject(
    val active: Boolean? = null,
    val clientId: Long? = null,
    val id: Long? = null,
    val workspaceId: Long? = null,
    val name: String? = null,
)
