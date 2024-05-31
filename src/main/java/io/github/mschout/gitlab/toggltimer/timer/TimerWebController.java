package io.github.mschout.gitlab.toggltimer.timer;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.gitlab4j.api.GitLabApiException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/timer")
@RequiredArgsConstructor
public class TimerWebController {

	private final TimerService timerService;

	@GetMapping("/create-project")
	ModelAndView createProject(@RequestParam String issueUrl, @RequestParam Long workspaceId,
			@RequestParam Long clientId) throws GitLabApiException {
		val mv = new ModelAndView("create-project");

		var project = timerService.createProject(new CreateProjectRequest(issueUrl, workspaceId, clientId));

		mv.addObject("project", project);

		return mv;
	}

	@GetMapping("/start")
	ModelAndView startTimer(@RequestParam String issueUrl, @RequestParam Long workspaceId, @RequestParam Long clientId)
			throws GitLabApiException {
		val mv = new ModelAndView("start-timer");

		val request = new StartTimerRequest();
		request.setIssueUrl(issueUrl);
		request.setWorkspaceId(workspaceId);
		request.setClientId(clientId);

		var start = timerService.startTimer(request);

		mv.addObject("startTime", start);

		return mv;
	}

}
