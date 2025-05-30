package io.github.mschout.gitlab.toggltimer.timer;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class TogglService {

  private final RestTemplate restTemplate;

  @Value("${toggl.baseUrl:https://api.track.toggl.com/api/v9}")
  private String baseUrl;

  @Value("${toggl.apiKey}")
  private String apiKey;

  @Getter(lazy = true)
  private final HttpHeaders authHeaders = buildAuthHeaders(apiKey);

  TogglProject findOrCreateProject(
      Long workspaceId, Long clientId, Long issueNumber, String issueTitle) {
    requireNonNull(workspaceId, "workspaceId is required");

    // Look for projects that match the issue number
    val uri =
        UriComponentsBuilder.fromUriString(baseUrl)
            .path("/workspaces/{workspaceId}/projects")
            .queryParam("name", issueNumber.toString())
            .build(workspaceId);

    val projectListType = new ParameterizedTypeReference<List<TogglProject>>() {};

    var request = new HttpEntity<>(getAuthHeaders());
    var result = restTemplate.exchange(uri, HttpMethod.GET, request, projectListType);

    // We should get empty array at least.
    if (result.getBody() == null) {
      throw new RuntimeException("No response body from toggl!");
    }

    log.info("Found projects: {}", result.getBody());

    // Filter results to ones that start with "$issueNumber -"
    var project =
        result.getBody().stream()
            .filter(p -> p.getName().startsWith(issueNumber + " -"))
            .findFirst();

    if (project.isPresent()) {
      log.info("Found toggl project: {}", project.get());
      return project.get();
    } else {
      log.info("Project not found in Toggl, creating project");
      return createToggleProject(workspaceId, clientId, issueNumber, issueTitle);
    }
  }

  private TogglProject createToggleProject(
      Long workspaceId, Long clientId, Long issueNumber, String projectName) {
    val uri =
        UriComponentsBuilder.fromUriString(baseUrl)
            .path("/workspaces/{workspaceId}/projects")
            .build(workspaceId);

    val project =
        TogglProject.builder()
            .active(true)
            .clientId(clientId)
            .name(issueNumber + " - " + projectName)
            .workspaceId(workspaceId)
            .build();

    var request = new HttpEntity<>(project, getAuthHeaders());
    var result = restTemplate.postForEntity(uri, request, TogglProject.class);

    if (result.getStatusCode().isError() || result.getBody() == null) {
      throw new RuntimeException("Failed to create project in Toggl: " + result.getBody());
    }

    log.info("Created project in Toggl: {}", result.getBody());

    return result.getBody();
  }

  Instant startTimer(TogglProject project, StartTimerRequest startRequest) {
    val uri =
        UriComponentsBuilder.fromUriString(baseUrl)
            .path("/workspaces/{workspaceId}/time_entries")
            .build(project.getWorkspaceId());

    var start = Optional.ofNullable(startRequest.getStart()).orElse(Instant.now());

    val timeEntry = new TogglTimeEntry(project.getWorkspaceId(), project.getId(), start);
    if (StringUtils.hasText(startRequest.getDescription())) {
      timeEntry.setDescription(startRequest.getDescription());
    }

    var request = new HttpEntity<>(timeEntry, getAuthHeaders());
    var result = restTemplate.postForEntity(uri, request, TogglTimeEntry.class);
    if (result.getStatusCode().isError() || result.getBody() == null) {
      throw new RuntimeException("Failed to start timer in Toggl: " + result.getBody());
    }

    log.info("Started timer in Toggl: {}", result.getBody());

    return timeEntry.getStart();
  }

  private HttpHeaders buildAuthHeaders(@NonNull String apiKey) {
    val headers = new HttpHeaders();
    headers.setBasicAuth(apiKey, "api_token");
    return headers;
  }
}
