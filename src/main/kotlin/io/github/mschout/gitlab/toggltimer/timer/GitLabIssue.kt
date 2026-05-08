package io.github.mschout.gitlab.toggltimer.timer

import org.springframework.web.util.UriComponentsBuilder

data class GitLabIssue(val groupName: String, val projectPath: String, val issueNumber: Long) {
  companion object {
    fun fromUrl(url: String): GitLabIssue {
      val pathSegments = UriComponentsBuilder.fromUriString(url).build().pathSegments
      require(pathSegments.size >= 5) { "Invalid GitLab issue URL: $url" }
      return GitLabIssue(
          groupName = pathSegments[0],
          projectPath = pathSegments[1],
          issueNumber = pathSegments[4].toLong(),
      )
    }
  }
}
