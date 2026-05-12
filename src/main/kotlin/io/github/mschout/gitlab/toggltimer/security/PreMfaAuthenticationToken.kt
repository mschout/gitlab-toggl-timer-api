package io.github.mschout.gitlab.toggltimer.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Partial-authentication token used between successful password verification and successful MFA
 * verification. Carries the verified principal but exposes only [ROLE_PRE_MFA] so the user cannot
 * reach any protected resource until MFA completes.
 */
class PreMfaAuthenticationToken(
    private val principalDetails: UserDetails,
    val pendingAuthorities: Collection<GrantedAuthority>,
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority(ROLE_PRE_MFA))) {

  init {
    isAuthenticated = true
  }

  override fun getCredentials(): Any = ""

  override fun getPrincipal(): Any = principalDetails

  override fun getName(): String = principalDetails.username

  companion object {
    const val ROLE_PRE_MFA = "ROLE_PRE_MFA"
  }
}
