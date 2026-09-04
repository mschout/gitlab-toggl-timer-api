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
package io.github.mschout.gitlab.toggltimer.toggl

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TogglTimeEntry(
    val workspaceId: Long? = null,
    val projectId: Long? = null,
    val projectName: String? = null,
    val projectColor: String? = null,
    val projectActive: Boolean? = null,
    val clientId: Long? = null,
    val clientName: String? = null,
    val taskId: Long? = null,
    val userId: Long? = null,
    val start: Instant? = null,
    val stop: Instant? = null,
    val description: String? = null,
    val duration: Long = -1L,
    val billable: Boolean? = null,
    val tags: List<String>? = null,
    val createdWith: String? = null,
    val at: Instant? = null,
    val serverDeletedAt: Instant? = null,
    val id: Long? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateStoppedTimeEntryRequest(
    val workspaceId: Long,
    val projectId: Long? = null,
    val taskId: Long? = null,
    val start: Instant,
    val stop: Instant,
    val description: String? = null,
    val duration: Long,
    val billable: Boolean,
    val tags: List<String>,
    val createdWith: String,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class UpdateTimeEntryDescriptionRequest(val workspaceId: Long, val description: String)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class UpdateTimeEntryProjectRequest(val workspaceId: Long, val projectId: Long)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class UpdateTimeEntryStartRequest(val workspaceId: Long, val start: Instant)
