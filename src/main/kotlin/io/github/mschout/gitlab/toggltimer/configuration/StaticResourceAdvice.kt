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

  @ModelAttribute("appStylesheetUrl")
  fun appStylesheetUrl(request: HttpServletRequest): String {
    val versionedPath =
        requireNotNull(resourceUrlProvider.getForLookupPath("/css/app.css")) {
          "Could not resolve the versioned app stylesheet"
        }
    return request.contextPath + versionedPath
  }
}
