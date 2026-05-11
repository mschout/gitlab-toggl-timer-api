package io.github.mschout.gitlab.toggltimer.toggl

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(TogglClientProperties::class)
class TogglClientConfiguration

@ConfigurationProperties(prefix = "toggl")
data class TogglClientProperties(
    @DefaultValue("https://api.track.toggl.com/api/v9") val baseUrl: String
)
