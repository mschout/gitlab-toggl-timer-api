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
