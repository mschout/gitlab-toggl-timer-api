package io.github.mschout.gitlab.toggltimer.timer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.gitlab4j.api.GitLabApiException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimerService {

	private final GitLabService gitLabService;

	private final TogglService togglService;

	Instant startTimer(StartTimerRequest startTimerRequest) throws GitLabApiException {
		val issue = startTimerRequest.getIssue();

		// look up issue in gitlab and return its title.
		val issueTitle = gitLabService.getGitlabIssueTitle(issue);

		// find the project in toggl, or create it if it doesn't exist.
		val project = togglService.findOrCreateProject(startTimerRequest.getWorkspaceId(),
				startTimerRequest.getClientId(), issue.getIssueNumber(), issueTitle);

		return togglService.startTimer(project, startTimerRequest);
	}

	TogglProject createProject(CreateProjectRequest createProjectRequest) throws GitLabApiException {
		val issue = createProjectRequest.getIssue();

		// look up issue in gitlab and return its title.
		val issueTitle = gitLabService.getGitlabIssueTitle(issue);

		return togglService.findOrCreateProject(createProjectRequest.getWorkspaceId(),
				createProjectRequest.getClientId(), issue.getIssueNumber(), issueTitle);
	}

}
