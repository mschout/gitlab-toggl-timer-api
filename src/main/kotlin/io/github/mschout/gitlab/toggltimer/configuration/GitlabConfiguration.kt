package io.github.mschout.gitlab.toggltimer.configuration

import org.gitlab4j.api.GitLabApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GitlabConfiguration(
    @param:Value("\${gitlab.accessToken}") private val accessToken: String,
    @param:Value("\${gitlab.url:https://gitlab.com}") private val gitlabUrl: String,
) {
  @Bean fun gitlabApi(): GitLabApi = GitLabApi(gitlabUrl, accessToken)
}
