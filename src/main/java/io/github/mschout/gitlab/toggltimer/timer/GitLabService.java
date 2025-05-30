package io.github.mschout.gitlab.toggltimer.timer;

import static io.github.mschout.gitlab.toggltimer.configuration.CacheManagerConfiguration.GITLAB_ISSUE_CACHE;
import static io.github.mschout.gitlab.toggltimer.configuration.CacheManagerConfiguration.GITLAB_PROJECT_CACHE;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Issue;
import org.gitlab4j.api.models.Project;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitLabService {

  private final GitLabApi gitLabApi;

  String getGitlabIssueTitle(GitLabIssue issue) throws GitLabApiException {
    val gitlabProject = getProject(issue.getGroupName(), issue.getProjectPath()).orElseThrow();

    log.info(
        "Found gitlab project for {}/{}: {}",
        issue.getGroupName(),
        issue.getProjectPath(),
        gitlabProject.getId());

    // and now we can get the issue title.
    val gitLabIssue =
        getGitlabProjectIssue(gitlabProject.getId(), issue.getIssueNumber()).orElseThrow();

    log.info(
        "Found gitlab issue for {}/{}: {}",
        issue.getGroupName(),
        issue.getProjectPath(),
        gitLabIssue.getTitle());

    return gitLabIssue.getTitle();
  }

  @Cacheable(GITLAB_ISSUE_CACHE)
  protected Optional<Issue> getGitlabProjectIssue(Long projectId, Long issueNumber)
      throws GitLabApiException {
    log.info("Logging up project {} issue {} using GitLab API", projectId, issueNumber);
    return Optional.ofNullable(gitLabApi.getIssuesApi().getIssue(projectId, issueNumber));
  }

  @Cacheable(GITLAB_PROJECT_CACHE)
  protected Optional<Project> getProject(String groupName, String projectPath)
      throws GitLabApiException {
    log.info("Log.info Looking up project {}/{} using GitLab API", groupName, projectPath);
    return gitLabApi
        .getSearchApi()
        .groupSearchStream(groupName, Constants.GroupSearchScope.PROJECTS, projectPath)
        .filter(p -> p.getClass().equals(Project.class))
        .map(Project.class::cast)
        .filter(p -> p.getPath().equals(projectPath))
        .findFirst();
  }
}
