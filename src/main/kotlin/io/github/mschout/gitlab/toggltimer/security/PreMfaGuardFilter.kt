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

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * If the SecurityContext holds a [PreMfaAuthenticationToken] (user is mid-MFA), redirect any
 * request outside of the MFA challenge / logout paths back to `/login/mfa`. This lets us reuse
 * Spring Security's default `.anyRequest().authenticated()` rule without weaving custom
 * AuthorizationManagers — the principal is technically authenticated, but we gate access here.
 */
@Component
class PreMfaGuardFilter : OncePerRequestFilter() {

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
    if (auth is PreMfaAuthenticationToken) {
      response.sendRedirect(request.contextPath + "/login/mfa")
      return
    }
    filterChain.doFilter(request, response)
  }

  companion object {
    private val WHITELIST =
        listOf(
            "/login",
            "/logout",
            "/webauthn",
            "/error",
            "/css",
            "/static",
            "/webjars",
            "/actuator/health",
        )
  }
}
