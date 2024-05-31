package io.github.mschout.gitlab.toggltimer.timer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.gitlab4j.api.GitLabApiException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/timer")
@RequiredArgsConstructor
@Tag(name = "Timer")
public class TimerController {

	private final TimerService timerService;

	@PostMapping("/start")
	@Operation(summary = "Start a timer for a GitLab issue")
	public Instant startTimer(@Validated @RequestBody StartTimerRequest startTimerRequest) throws GitLabApiException {
		return timerService.startTimer(startTimerRequest);
	}

	@PostMapping("/create-project")
	@Operation(summary = "Create a project in Toggl for a gitlab issue")
	public TogglProject createProject(@Validated @RequestBody CreateProjectRequest createProjectRequest)
			throws GitLabApiException {
		return timerService.createProject(createProjectRequest);
	}

}
