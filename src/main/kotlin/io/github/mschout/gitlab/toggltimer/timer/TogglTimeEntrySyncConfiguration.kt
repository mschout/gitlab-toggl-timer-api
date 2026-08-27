/*
 * Copyright 2026 Michael Schout
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.mschout.gitlab.toggltimer.timer

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@EnableConfigurationProperties(TogglTimeEntrySyncProperties::class)
class TogglTimeEntrySyncConfiguration

@ConfigurationProperties(prefix = "app.toggl-sync")
data class TogglTimeEntrySyncProperties(
    val enabled: Boolean = true,
    val interval: Duration = Duration.ofMinutes(15),
    val initialDelay: Duration = Duration.ofSeconds(30),
    val initialLookback: Duration = Duration.ofDays(7),
)
