package io.github.mschout.gitlab.toggltimer.timer

import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Service
class TogglService(
    private val restTemplate: RestTemplate,
    @param:Value("\${toggl.baseUrl:https://api.track.toggl.com/api/v9}")
    private val baseUrl: String,
    @param:Value("\${toggl.apiKey}") private val apiKey: String,
) {

  private val authHeaders: HttpHeaders by lazy {
    HttpHeaders().apply { setBasicAuth(apiKey, "api_token") }
  }

  fun findOrCreateProject(
      workspaceId: Long,
      clientId: Long,
      issueNumber: Long,
      issueTitle: String,
  ): TogglProject {
    val uri =
        UriComponentsBuilder.fromUriString(baseUrl)
            .path("/workspaces/{workspaceId}/projects")
            .queryParam("name", issueNumber.toString())
            .build(workspaceId)

    val result =
        restTemplate.exchange(
            uri,
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders),
            object : ParameterizedTypeReference<List<TogglProject>>() {},
        )

    val projects = result.body ?: error("No response body from toggl!")

    log.info("Found projects: {}", projects)

    return projects
        .firstOrNull { it.name?.startsWith("$issueNumber -") == true }
        ?.also { log.info("Found toggl project: {}", it) }
        ?: run {
          log.info("Project not found in Toggl, creating project")
          createToggleProject(workspaceId, clientId, issueNumber, issueTitle)
        }
  }

  private fun createToggleProject(
      workspaceId: Long,
      clientId: Long,
      issueNumber: Long,
      projectName: String,
  ): TogglProject {
    val uri =
        UriComponentsBuilder.fromUriString(baseUrl)
            .path("/workspaces/{workspaceId}/projects")
            .build(workspaceId)

    val project =
        TogglProject(
            active = true,
            clientId = clientId,
            name = "$issueNumber - $projectName",
            workspaceId = workspaceId,
        )

    val request = HttpEntity(project, authHeaders)
    val result = restTemplate.postForEntity(uri, request, TogglProject::class.java)
    val body = result.body
    if (result.statusCode.isError || body == null) {
      error("Failed to create project in Toggl: ${result.body}")
    }

    log.info("Created project in Toggl: {}", body)

    return body
  }

  fun startTimer(project: TogglProject, startRequest: StartTimerRequest): Instant {
    val workspaceId = checkNotNull(project.workspaceId) { "Toggl project missing workspaceId" }
    val projectId = checkNotNull(project.id) { "Toggl project missing id" }

    val uri =
        UriComponentsBuilder.fromUriString(baseUrl)
            .path("/workspaces/{workspaceId}/time_entries")
            .build(workspaceId)

    val timeEntry =
        TogglTimeEntry(
            workspaceId = workspaceId,
            projectId = projectId,
            start = startRequest.start ?: Instant.now(),
            description = startRequest.description?.takeIf { it.isNotBlank() },
        )

    val request = HttpEntity(timeEntry, authHeaders)
    val result = restTemplate.postForEntity(uri, request, TogglTimeEntry::class.java)
    val body = result.body
    if (result.statusCode.isError || body == null) {
      error("Failed to start timer in Toggl: ${result.body}")
    }

    log.info("Started timer in Toggl: {}", body)

    return timeEntry.start
  }

  companion object {
    private val log = LoggerFactory.getLogger(TogglService::class.java)
  }
}
