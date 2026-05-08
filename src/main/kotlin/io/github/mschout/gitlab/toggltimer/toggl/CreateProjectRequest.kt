package io.github.mschout.gitlab.toggltimer.toggl

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CreateProjectRequest(
    val name: String,
    val clientId: String,
    val color: String? = null,
    val billable: Boolean? = null,
    val active: Boolean = true,
    val template: Boolean = false,
) {}
