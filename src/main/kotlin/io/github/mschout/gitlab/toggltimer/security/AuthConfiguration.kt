package io.github.mschout.gitlab.toggltimer.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.context.annotation.Configuration

@Configuration @EnableConfigurationProperties(AuthProperties::class) class AuthConfiguration

@ConfigurationProperties(prefix = "app.auth")
data class AuthProperties(
    @DefaultValue("true") val passwordLoginEnabled: Boolean,
    @DefaultValue("GitLab Toggl Timer") val rpName: String,
    @DefaultValue("localhost") val rpId: String,
    @DefaultValue("http://localhost:8080") val origins: Set<String>,
)
