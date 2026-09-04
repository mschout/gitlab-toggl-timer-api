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
package io.github.mschout.gitlab.toggltimer.configuration

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.servlet.resource.ResourceUrlProvider

@ControllerAdvice
class StaticResourceAdvice(private val resourceUrlProvider: ResourceUrlProvider) {

  @ModelAttribute("staticResourceUrls")
  fun staticResourceUrls(request: HttpServletRequest): StaticResourceUrls =
      StaticResourceUrls(
          appStylesheet = resourceUrl(request, "/css/app.css"),
          baseScript = resourceUrl(request, "/js/base.js"),
          loginScript = resourceUrl(request, "/js/login.js"),
          settingsScript = resourceUrl(request, "/js/settings.js"),
          settingsMfaScript = resourceUrl(request, "/js/settings-mfa.js"),
          timerScript = resourceUrl(request, "/js/timer.js"),
      )

  private fun resourceUrl(request: HttpServletRequest, path: String): String {
    val versionedPath =
        requireNotNull(resourceUrlProvider.getForLookupPath(path)) {
          "Could not resolve the versioned static resource: $path"
        }
    return request.contextPath + versionedPath
  }
}

data class StaticResourceUrls(
    val appStylesheet: String,
    val baseScript: String,
    val loginScript: String,
    val settingsScript: String,
    val settingsMfaScript: String,
    val timerScript: String,
)
