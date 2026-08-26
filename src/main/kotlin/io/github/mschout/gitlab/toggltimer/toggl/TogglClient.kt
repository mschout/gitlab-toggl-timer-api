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

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PatchExchange
import org.springframework.web.service.annotation.PostExchange
import org.springframework.web.service.annotation.PutExchange

@HttpExchange("https://api.track.toggl.com/api/v9")
interface TogglClient {
  @GetExchange("/workspaces") fun getWorkspaces(): List<TogglWorkspace>

  @GetExchange("/workspaces/{workspaceId}/projects")
  fun getProjects(
      @PathVariable workspaceId: Long,
      @RequestParam(required = false) name: String? = null,
  ): List<TogglProject>

  @GetExchange("/workspaces/{workspaceId}/projects/paginated")
  fun getProjectsPaginated(
      @PathVariable workspaceId: Long,
      @RequestParam(value = "start_project_id", required = false) startProjectId: Long? = null,
  ): List<TogglProject>

  @GetExchange("/workspaces/{workspaceId}/projects/{projectId}")
  fun getProject(@PathVariable workspaceId: Long, @PathVariable projectId: Long): TogglProject

  @PostExchange("/workspaces/{workspaceId}/projects")
  fun createProject(
      @PathVariable workspaceId: Long,
      @RequestBody project: CreateProjectRequest,
  ): TogglProject

  @GetExchange("/workspaces/{workspaceId}/clients")
  fun getClients(@PathVariable workspaceId: Long): List<TogglWorkspaceClient>

  @GetExchange("/me/time_entries/current") fun getCurrentTimeEntry(): TogglTimeEntry?

  @GetExchange("/me/time_entries")
  fun getTimeEntries(
      @RequestParam("start_date") startDate: String,
      @RequestParam("end_date") endDate: String,
      @RequestParam("meta") meta: Boolean = true,
  ): List<TogglTimeEntry>

  @PostExchange("/workspaces/{workspaceId}/time_entries")
  fun createTimeEntry(
      @PathVariable workspaceId: Long,
      @RequestBody entry: TogglTimeEntry,
  ): TogglTimeEntry

  @PutExchange("/workspaces/{workspaceId}/time_entries/{timeEntryId}")
  fun updateTimeEntry(
      @PathVariable workspaceId: Long,
      @PathVariable timeEntryId: Long,
      @RequestBody entry: TogglTimeEntry,
  ): TogglTimeEntry

  @PutExchange("/workspaces/{workspaceId}/time_entries/{timeEntryId}")
  fun updateTimeEntryDescription(
      @PathVariable workspaceId: Long,
      @PathVariable timeEntryId: Long,
      @RequestBody request: UpdateTimeEntryDescriptionRequest,
  ): TogglTimeEntry

  @PutExchange("/workspaces/{workspaceId}/time_entries/{timeEntryId}")
  fun updateTimeEntryProject(
      @PathVariable workspaceId: Long,
      @PathVariable timeEntryId: Long,
      @RequestBody request: UpdateTimeEntryProjectRequest,
  ): TogglTimeEntry

  @PatchExchange("/workspaces/{workspaceId}/time_entries/{timeEntryId}/stop")
  fun stopTimeEntry(
      @PathVariable workspaceId: Long,
      @PathVariable timeEntryId: Long,
  ): TogglTimeEntry
}
