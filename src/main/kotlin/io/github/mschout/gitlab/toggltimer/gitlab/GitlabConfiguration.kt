package io.github.mschout.gitlab.toggltimer.gitlab

import org.gitlab4j.api.GitLabApi
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(GitlabConfigurationProperties::class)
class GitlabConfiguration(val gitlabProperties: GitlabConfigurationProperties) {
  @Bean fun gitlabApi(): GitLabApi = GitLabApi(gitlabProperties.url, gitlabProperties.accessToken)
}

@ConfigurationProperties(prefix = "gitlab")
class GitlabConfigurationProperties(
    /** GitLab access token. */
    val accessToken: String,

    /** GitLab instance URL. Default: https://gitlab.com */
    val url: String = "https://gitlab.com",
)
