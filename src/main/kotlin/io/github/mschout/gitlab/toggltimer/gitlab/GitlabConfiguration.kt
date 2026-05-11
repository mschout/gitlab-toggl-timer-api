package io.github.mschout.gitlab.toggltimer.gitlab

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(GitlabConfigurationProperties::class)
class GitlabConfiguration

@ConfigurationProperties(prefix = "gitlab")
class GitlabConfigurationProperties(
    /** GitLab instance URL. Default: https://gitlab.com */
    @DefaultValue("https://gitlab.com") val url: String
)
