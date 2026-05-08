package io.github.mschout.gitlab.toggltimer.timer

import java.time.Instant
import org.springframework.stereotype.Service

@Service
class TimerService(
    private val gitLabService: GitLabService,
    private val togglService: TogglService,
) {

  fun startTimer(startTimerRequest: StartTimerRequest): Instant {
    val issue = startTimerRequest.issue()
    val issueTitle = gitLabService.getGitlabIssueTitle(issue)

    val project =
        togglService.findOrCreateProject(
            startTimerRequest.workspaceId,
            startTimerRequest.clientId,
            issue.issueNumber,
            issueTitle,
        )

    return togglService.startTimer(project, startTimerRequest)
  }

  fun createProject(createProjectRequest: CreateProjectRequest): TogglProject {
    val issue = createProjectRequest.issue()
    val issueTitle = gitLabService.getGitlabIssueTitle(issue)

    return togglService.findOrCreateProject(
        createProjectRequest.workspaceId,
        createProjectRequest.clientId,
        issue.issueNumber,
        issueTitle,
    )
  }
}
