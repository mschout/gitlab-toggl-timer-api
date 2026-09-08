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

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser

class UserProfileServiceTest {
  private val credentialsService = mockk<CurrentUserCredentialsService>()
  private val service = UserProfileService(credentialsService)

  @Test
  fun `ID token claims take precedence over UserInfo without querying the database`() {
    val profile =
        service.profile(
            oidc(
                mapOf(
                    "email" to "token@example.com",
                    "name" to "Token Name",
                    "picture" to "https://example.com/token.png",
                ),
                mapOf(
                    "email" to "info@example.com",
                    "name" to "Info Name",
                    "picture" to "https://example.com/info.png",
                ),
            )
        )
    profile?.email shouldBe "token@example.com"
    profile?.fullName shouldBe "Token Name"
    profile?.pictureUrl shouldBe "https://example.com/token.png"
    verify(exactly = 0) { credentialsService.currentUserOrNull() }
  }

  @Test
  fun `missing and blank ID token fields fall back to UserInfo independently`() {
    val profile =
        service.profile(
            oidc(
                mapOf("name" to " ", "email" to "token@example.com"),
                mapOf(
                    "email" to "info@example.com",
                    "name" to "Info Name",
                    "picture" to "https://example.com/info.png",
                ),
            )
        )
    profile?.email shouldBe "token@example.com"
    profile?.fullName shouldBe "Info Name"
    profile?.pictureUrl shouldBe "https://example.com/info.png"
  }

  @Test
  fun `Gravatar uses normalized email and the documented SHA256 hash`() {
    val profile = service.profile(oidc(mapOf("email" to "MyEmailAddress@example.com ")))
    profile?.gravatarUrl shouldBe
        "https://gravatar.com/avatar/84059b07d4be67b806386c0aad8070a23f18836bbaae342275dc0a83414c32ee?s=96&d=404&r=g"
    profile?.pictureUrl shouldBe null
    profile?.fullName shouldBe null
  }

  @ParameterizedTest
  @ValueSource(
      strings =
          [
              "javascript:alert(1)",
              "data:image/png;base64,abc",
              "/avatar.png",
              "https://",
              "https://bad host/avatar",
              "https://user:password@example.com/avatar",
          ]
  )
  fun `invalid picture URLs fall back to Gravatar`(picture: String) {
    val profile =
        service.profile(oidc(mapOf("email" to "MyEmailAddress@example.com ", "picture" to picture)))
    profile?.pictureUrl shouldBe null
    profile?.gravatarUrl shouldBe
        "https://gravatar.com/avatar/84059b07d4be67b806386c0aad8070a23f18836bbaae342275dc0a83414c32ee?s=96&d=404&r=g"
  }

  @ParameterizedTest
  @ValueSource(strings = ["https://example.com/avatar", "http://example.com/avatar"])
  fun `absolute HTTP and HTTPS picture URLs are accepted`(picture: String) {
    service.profile(oidc(mapOf("picture" to picture)))?.pictureUrl shouldBe picture
  }

  @Test
  fun `missing email skips Gravatar and uses the principal identity label`() {
    val profile = service.profile(oidc(mapOf("name" to " ")))
    profile?.email shouldBe "subject"
    profile?.gravatarUrl shouldBe null
    profile?.fullName shouldBe null
  }

  @Test
  fun `local authentication uses the stored profile`() {
    every { credentialsService.currentUserOrNull() } returns
        User(email = "alice@example.com", displayName = "Alice Example")
    val profile = service.profile(localAuthentication())
    profile?.email shouldBe "alice@example.com"
    profile?.fullName shouldBe "Alice Example"
    profile?.pictureUrl shouldBe null
  }

  @Test
  fun `missing local record preserves the identity without inventing an email`() {
    every { credentialsService.currentUserOrNull() } returns null
    val profile = service.profile(localAuthentication())
    profile?.email shouldBe "alice"
    profile?.fullName shouldBe null
    profile?.gravatarUrl shouldBe null
  }

  @Test
  fun `anonymous and unauthenticated requests have no profile`() {
    service.profile(null) shouldBe null
    service.profile(
        UsernamePasswordAuthenticationToken.unauthenticated("alice", "password")
    ) shouldBe null
    service.profile(
        AnonymousAuthenticationToken(
            "key",
            "anonymousUser",
            listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")),
        )
    ) shouldBe null
    verify(exactly = 0) { credentialsService.currentUserOrNull() }
  }

  private fun localAuthentication() =
      UsernamePasswordAuthenticationToken.authenticated(
          "alice",
          null,
          listOf(SimpleGrantedAuthority("ROLE_USER")),
      )

  private fun oidc(
      tokenClaims: Map<String, Any>,
      infoClaims: Map<String, Any> = emptyMap(),
  ): UsernamePasswordAuthenticationToken {
    val now = Instant.parse("2026-09-08T12:00:00Z")
    val token =
        OidcIdToken("token", now, now.plusSeconds(3600), mapOf("sub" to "subject") + tokenClaims)
    val userInfo = OidcUserInfo(mapOf("sub" to "subject") + infoClaims)
    val principal = DefaultOidcUser(emptyList(), token, userInfo)
    return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities)
  }
}
