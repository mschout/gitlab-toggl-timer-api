package io.github.mschout.gitlab.toggltimer.security

import io.github.mschout.gitlab.toggltimer.mfa.MfaService
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler

/**
 * After a successful password login: if the user has any MFA factor enrolled, downgrade the
 * security context to a [PreMfaAuthenticationToken] and send the user to `/login/mfa`. Otherwise
 * proceed to the configured default success URL.
 */
class MfaAwareAuthenticationSuccessHandler(
    private val userRepository: UserRepository,
    private val mfaService: MfaService,
    defaultSuccessUrl: String,
    mfaChallengeUrl: String = "/login/mfa",
) : AuthenticationSuccessHandler {

  private val defaultDelegate =
      SimpleUrlAuthenticationSuccessHandler(defaultSuccessUrl).apply {
        setAlwaysUseDefaultTargetUrl(true)
      }
  private val mfaDelegate =
      SimpleUrlAuthenticationSuccessHandler(mfaChallengeUrl).apply {
        setAlwaysUseDefaultTargetUrl(true)
      }

  override fun onAuthenticationSuccess(
      request: HttpServletRequest,
      response: HttpServletResponse,
      authentication: Authentication,
  ) {
    val email = authentication.name
    val user = email?.let { userRepository.findByEmail(it) }
    if (user != null && mfaService.hasAnyMfa(user)) {
      val principal = authentication.principal as? UserDetails ?: SimpleUserDetails(email)
      val preMfa = PreMfaAuthenticationToken(principal, authentication.authorities)
      preMfa.details = authentication.details
      SecurityContextHolder.getContext().authentication = preMfa
      mfaDelegate.onAuthenticationSuccess(request, response, preMfa)
      return
    }
    defaultDelegate.onAuthenticationSuccess(request, response, authentication)
  }

  private class SimpleUserDetails(private val email: String) : UserDetails {
    override fun getAuthorities() = emptyList<org.springframework.security.core.GrantedAuthority>()

    override fun getPassword() = ""

    override fun getUsername() = email

    override fun isAccountNonExpired() = true

    override fun isAccountNonLocked() = true

    override fun isCredentialsNonExpired() = true

    override fun isEnabled() = true
  }
}
