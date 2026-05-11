package io.github.mschout.gitlab.toggltimer.gitlab

import org.gitlab4j.api.GitLabApi
import org.springframework.stereotype.Component

@Component
class GitLabApiFactory(private val props: GitlabConfigurationProperties) {
  fun forToken(token: String): GitLabApi = GitLabApi(props.url, token)
}
