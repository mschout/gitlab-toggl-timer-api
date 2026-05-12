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
