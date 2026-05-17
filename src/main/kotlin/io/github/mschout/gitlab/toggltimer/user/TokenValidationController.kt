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
package io.github.mschout.gitlab.toggltimer.user

import io.github.mschout.gitlab.toggltimer.gitlab.GitLabApiFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotBlank
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

data class ValidateGitlabRequest(@field:NotBlank val token: String)

data class ValidateTogglRequest(@field:NotBlank val apiKey: String)

data class TokenValidationResult(val valid: Boolean)

@RestController
@RequestMapping("/settings/validate")
@Tag(name = "Token validation")
class TokenValidationController(
    private val gitLabApiFactory: GitLabApiFactory,
    private val togglClientFactory: TogglClientFactory,
) {

  @PostMapping("/gitlab")
  @Operation(summary = "Check whether a GitLab personal access token authenticates successfully")
  fun validateGitlab(
      @Validated @RequestBody request: ValidateGitlabRequest
  ): TokenValidationResult =
      runCatching { gitLabApiFactory.forToken(request.token).userApi.currentUser }
          .fold(
              onSuccess = { TokenValidationResult(valid = it != null) },
              onFailure = {
                logger.warn(it) { "GitLab token validation failed" }
                TokenValidationResult(valid = false)
              },
          )

  @PostMapping("/toggl")
  @Operation(summary = "Check whether a Toggl API key authenticates successfully")
  fun validateToggl(@Validated @RequestBody request: ValidateTogglRequest): TokenValidationResult =
      runCatching { togglClientFactory.forApiKey(request.apiKey).getWorkspaces() }
          .fold(
              onSuccess = { TokenValidationResult(valid = true) },
              onFailure = {
                logger.warn(it) { "Toggl API key validation failed" }
                TokenValidationResult(valid = false)
              },
          )
}
