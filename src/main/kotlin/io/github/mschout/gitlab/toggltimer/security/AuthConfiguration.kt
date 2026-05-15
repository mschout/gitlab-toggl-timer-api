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
