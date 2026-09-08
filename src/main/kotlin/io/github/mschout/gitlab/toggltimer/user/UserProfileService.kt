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

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service

@Service
class UserProfileService(private val credentialsService: CurrentUserCredentialsService) {

  fun profile(authentication: Authentication?): UserProfileView? {
    if (
        authentication == null ||
            !authentication.isAuthenticated ||
            authentication is AnonymousAuthenticationToken
    )
        return null

    val principal = authentication.principal
    if (principal is OidcUser) {
      return profile(
          email = claim(principal, "email"),
          fullName = claim(principal, "name"),
          picture = claim(principal, "picture"),
          fallbackLabel = authentication.name,
      )
    }

    val user = credentialsService.currentUserOrNull()
    return profile(
        email = user?.email?.takeIf { it.isNotBlank() },
        fullName = user?.displayName?.takeIf { it.isNotBlank() },
        picture = null,
        fallbackLabel = authentication.name,
    )
  }

  private fun claim(user: OidcUser, name: String): String? =
      (user.idToken.claims[name] as? String)?.takeIf { it.isNotBlank() }
          ?: (user.userInfo?.claims?.get(name) as? String)?.takeIf { it.isNotBlank() }

  private fun profile(
      email: String?,
      fullName: String?,
      picture: String?,
      fallbackLabel: String?,
  ): UserProfileView =
      UserProfileView(
          email = email ?: fallbackLabel?.takeIf { it.isNotBlank() } ?: "User",
          fullName = fullName,
          pictureUrl = picture?.takeIf { isImageUrl(it) },
          gravatarUrl = email?.let { gravatarUrl(it) },
      )

  private fun isImageUrl(value: String): Boolean =
      runCatching {
            val uri = URI(value)
            uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null
          }
          .getOrDefault(false)

  private fun gravatarUrl(email: String): String {
    val normalizedEmail = email.trim().lowercase(Locale.ROOT)
    val hash =
        MessageDigest.getInstance("SHA-256")
            .digest(normalizedEmail.toByteArray(StandardCharsets.UTF_8))
            .toHexString()
    return "https://gravatar.com/avatar/$hash?s=96&d=404&r=g"
  }
}

data class UserProfileView(
    val email: String,
    val fullName: String?,
    val pictureUrl: String?,
    val gravatarUrl: String?,
)
