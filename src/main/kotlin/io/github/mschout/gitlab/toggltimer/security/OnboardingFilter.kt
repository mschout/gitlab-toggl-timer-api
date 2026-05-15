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

import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.github.mschout.gitlab.toggltimer.user.UserSettingsRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class OnboardingFilter(
    private val userRepository: UserRepository,
    private val userSettingsRepository: UserSettingsRepository,
) : OncePerRequestFilter() {

  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    val path = request.requestURI.removePrefix(request.contextPath)
    return WHITELIST.any { prefix -> path == prefix || path.startsWith("$prefix/") }
  }

  override fun doFilterInternal(
      request: HttpServletRequest,
      response: HttpServletResponse,
      filterChain: FilterChain,
  ) {
    val auth = SecurityContextHolder.getContext().authentication
    if (auth == null || !auth.isAuthenticated || auth is AnonymousAuthenticationToken) {
      filterChain.doFilter(request, response)
      return
    }
    val email = auth.name?.takeIf { it.isNotBlank() }
    val user = email?.let { userRepository.findByEmail(it) }
    if (user == null) {
      filterChain.doFilter(request, response)
      return
    }
    val settings = userSettingsRepository.findById(user.id).orElse(null)
    val configured =
        settings != null &&
            !settings.gitlabAccessToken.isNullOrBlank() &&
            !settings.togglApiKey.isNullOrBlank() &&
            settings.togglWorkspaceId != null
    if (!configured) {
      response.sendRedirect(request.contextPath + "/settings")
      return
    }
    filterChain.doFilter(request, response)
  }

  companion object {
    private val WHITELIST =
        listOf(
            "/settings",
            "/logout",
            "/login",
            "/oauth2",
            "/webauthn",
            "/error",
            "/css",
            "/static",
            "/webjars",
            "/actuator/health",
        )
  }
}
