package io.github.mschout.gitlab.toggltimer.timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.util.UriComponentsBuilder;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
class GitLabIssue {

  private final String groupName;

  private final String projectPath;

  private final Long issueNumber;

  static GitLabIssue fromUrl(String url) {
    var ucb = UriComponentsBuilder.fromUriString(url).build();

    // "group/project/-/issues/12345"
    var pathSegments = ucb.getPathSegments();
    if (pathSegments.size() < 5) {
      throw new IllegalArgumentException("Invalid GitLab issue URL: " + url);
    }

    val groupName = pathSegments.get(0);
    val projectPath = pathSegments.get(1);
    val issueNumber = Long.parseLong(pathSegments.get(4));

    return new GitLabIssue(groupName, projectPath, issueNumber);
  }
}
