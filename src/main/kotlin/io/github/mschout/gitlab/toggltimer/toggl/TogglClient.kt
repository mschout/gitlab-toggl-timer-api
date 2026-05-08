package io.github.mschout.gitlab.toggltimer.toggl

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange("https://api.track.toggl.com/api/v9")
interface TogglClient {
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
}
