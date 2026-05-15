package io.github.mschout.gitlab.toggltimer.toggl

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
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
}
