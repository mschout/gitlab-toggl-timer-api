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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.Optional
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder

class CurrentUserCredentialsServiceTest {

  private val userRepo = mockk<UserRepository>()
  private val settingsRepo = mockk<UserSettingsRepository>()
  private val service = CurrentUserCredentialsService(userRepo, settingsRepo)

  @AfterEach
  fun clearAuth() {
    SecurityContextHolder.clearContext()
  }

  private fun authenticateAs(email: String) {
    SecurityContextHolder.getContext().authentication =
        UsernamePasswordAuthenticationToken(email, "n/a", AuthorityUtils.NO_AUTHORITIES)
  }

  @Test
  fun `currentUserOrNull returns null when no auth`() {
    service.currentUserOrNull() shouldBe null
  }

  @Test
  fun `currentUserOrNull returns null for anonymous auth`() {
    SecurityContextHolder.getContext().authentication =
        AnonymousAuthenticationToken(
            "anon",
            "anon",
            AuthorityUtils.createAuthorityList("ROLE_ANON"),
        )
    service.currentUserOrNull() shouldBe null
  }

  @Test
  fun `currentUser throws when no local user found`() {
    authenticateAs("ghost@example.com")
    every { userRepo.findByEmail("ghost@example.com") } returns null
    shouldThrow<IllegalStateException> { service.currentUser() }
  }

  @Test
  fun `requireGitlabToken returns saved token`() {
    val user = User(email = "alice@example.com", id = 1L)
    authenticateAs("alice@example.com")
    every { userRepo.findByEmail("alice@example.com") } returns user
    every { settingsRepo.findById(1L) } returns
        Optional.of(UserSettings(user = user, gitlabAccessToken = "glpat-xyz", userId = 1L))

    service.requireGitlabToken() shouldBe "glpat-xyz"
  }

  @Test
  fun `requireGitlabToken throws when settings missing`() {
    val user = User(email = "alice@example.com", id = 1L)
    authenticateAs("alice@example.com")
    every { userRepo.findByEmail("alice@example.com") } returns user
    every { settingsRepo.findById(1L) } returns Optional.empty()

    val ex = shouldThrow<MissingCredentialsException> { service.requireGitlabToken() }
    ex.credentialKind shouldBe "gitlab"
  }

  @Test
  fun `requireTogglApiKey throws when blank`() {
    val user = User(email = "alice@example.com", id = 1L)
    authenticateAs("alice@example.com")
    every { userRepo.findByEmail("alice@example.com") } returns user
    every { settingsRepo.findById(1L) } returns
        Optional.of(UserSettings(user = user, togglApiKey = "  ", userId = 1L))

    shouldThrow<MissingCredentialsException> { service.requireTogglApiKey() }
  }
}
