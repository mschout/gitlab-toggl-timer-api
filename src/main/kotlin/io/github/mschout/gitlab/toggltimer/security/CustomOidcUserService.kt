package io.github.mschout.gitlab.toggltimer.security

import io.github.mschout.gitlab.toggltimer.user.User
import io.github.mschout.gitlab.toggltimer.user.UserAuthIdentity
import io.github.mschout.gitlab.toggltimer.user.UserAuthIdentityRepository
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.oidc.StandardClaimNames
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class CustomOidcUserService(
    private val userRepository: UserRepository,
    private val identityRepository: UserAuthIdentityRepository,
) : OidcUserService() {

  override fun loadUser(userRequest: OidcUserRequest): OidcUser {
    val oidcUser = super.loadUser(userRequest)
    val provider = userRequest.clientRegistration.registrationId
    val subject = oidcUser.subject
    val email =
        requireNotNull(oidcUser.email) {
          "OIDC userinfo missing 'email' claim — ensure the provider is configured to release the email scope"
        }
    val displayName = oidcUser.fullName ?: oidcUser.preferredUsername

    val user = findOrCreate(provider, subject, email, displayName)

    val authorities: Collection<GrantedAuthority> =
        user.roles.map { SimpleGrantedAuthority(it) } +
            OidcUserAuthority(oidcUser.idToken, oidcUser.userInfo)

    return DefaultOidcUser(
        authorities,
        oidcUser.idToken,
        oidcUser.userInfo,
        StandardClaimNames.EMAIL,
    )
  }

  @Transactional
  fun findOrCreate(provider: String, subject: String, email: String, displayName: String?): User {
    identityRepository.findByProviderAndSubject(provider, subject)?.let { identity ->
      // Re-fetch the user by id so the eagerly-mapped roles collection is loaded
      // even if @Transactional did not actually intercept this call (Spring AOP
      // self-invocation via the OIDC filter chain is fragile here). Accessing
      // identity.user.id on the lazy proxy is safe — Hibernate exposes @Id
      // without triggering initialization.
      return userRepository.findById(identity.user.id).orElseThrow {
        IllegalStateException("OIDC identity ${identity.id} references missing user")
      }
    }

    val user =
        userRepository.findByEmail(email)
            ?: userRepository.save(User(email = email, displayName = displayName)).also {
              log.info {
                "Provisioned new OIDC user id=${it.id} email=${it.email} provider=$provider"
              }
            }

    if (displayName != null && user.displayName == null) {
      user.displayName = displayName
    }

    identityRepository.save(UserAuthIdentity(provider = provider, subject = subject, user = user))
    return user
  }
}
