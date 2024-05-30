package io.github.mschout.gitlab.toggltimer.timer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Issue;
import org.gitlab4j.api.models.Project;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitLabService {

	private final GitLabApi gitLabApi;

	String getGitlabIssueTitle(GitLabIssue issue) throws GitLabApiException {
		val gitlabProject = getProject(issue.getGroupName(), issue.getProjectPath()).orElseThrow();

		log.info("Found gitlab project for {}/{}: {}", issue.getGroupName(), issue.getProjectPath(),
				gitlabProject.getId());

		// and now we can get the issue title.
		val gitLabIssue = getGitlabProjectIssue(gitlabProject.getId(), issue.getIssueNumber()).orElseThrow();

		log.info("Found gitlab issue for {}/{}: {}", issue.getGroupName(), issue.getProjectPath(),
				gitLabIssue.getTitle());

		return gitLabIssue.getTitle();
	}

	// TODO: should cache this probably.
	private Optional<Issue> getGitlabProjectIssue(Long projectId, Long issueNumber) throws GitLabApiException {
		return Optional.ofNullable(gitLabApi.getIssuesApi().getIssue(projectId, issueNumber));
	}

	// TODO: should cache this probably.
	private Optional<Project> getProject(String groupName, String projectPath) throws GitLabApiException {
		return gitLabApi.getSearchApi()
			.groupSearchStream(groupName, Constants.GroupSearchScope.PROJECTS, projectPath)
			.filter(p -> p.getClass().equals(Project.class))
			.map(Project.class::cast)
			.filter(p -> p.getPath().equals(projectPath))
			.findFirst();
	}

}
