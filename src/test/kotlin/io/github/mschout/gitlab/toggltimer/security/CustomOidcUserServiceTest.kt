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

import io.github.mschout.gitlab.toggltimer.user.User
import io.github.mschout.gitlab.toggltimer.user.UserAuthIdentity
import io.github.mschout.gitlab.toggltimer.user.UserAuthIdentityRepository
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional
import org.junit.jupiter.api.Test

class CustomOidcUserServiceTest {
  private val userRepo = mockk<UserRepository>()
  private val identityRepo = mockk<UserAuthIdentityRepository>()
  private val service = CustomOidcUserService(userRepo, identityRepo)

  init {
    every { identityRepo.save(any<UserAuthIdentity>()) } answers { firstArg() }
  }

  @Test
  fun `creates new user and identity when subject and email are unknown`() {
    every { identityRepo.findByProviderAndSubject("oidc", "sub-1") } returns null
    every { userRepo.findByEmail("alice@example.com") } returns null
    val savedUser = slot<User>()
    every { userRepo.save(capture(savedUser)) } answers { savedUser.captured }

    val result = service.findOrCreate("oidc", "sub-1", "alice@example.com", "Alice")

    result.email shouldBe "alice@example.com"
    result.displayName shouldBe "Alice"
    verify {
      identityRepo.save(match<UserAuthIdentity> { it.provider == "oidc" && it.subject == "sub-1" })
    }
  }

  @Test
  fun `links identity to existing user when email matches`() {
    val existing = User(email = "bob@example.com", displayName = "Bob")
    every { identityRepo.findByProviderAndSubject("oidc", "sub-2") } returns null
    every { userRepo.findByEmail("bob@example.com") } returns existing

    val result = service.findOrCreate("oidc", "sub-2", "bob@example.com", "Bob")

    result shouldBe existing
    verify(exactly = 0) { userRepo.save(any()) }
    verify {
      identityRepo.save(match<UserAuthIdentity> { it.user === existing && it.subject == "sub-2" })
    }
  }

  @Test
  fun `returns existing user when provider and subject already linked`() {
    val existing = User(email = "carol@example.com", displayName = "Carol", id = 99L)
    val identity = UserAuthIdentity(provider = "oidc", subject = "sub-3", user = existing)
    every { identityRepo.findByProviderAndSubject("oidc", "sub-3") } returns identity
    every { userRepo.findById(99L) } returns Optional.of(existing)

    val result = service.findOrCreate("oidc", "sub-3", "carol@example.com", "Carol")

    result shouldBe existing
    verify(exactly = 0) { userRepo.findByEmail(any()) }
    verify(exactly = 0) { userRepo.save(any()) }
    verify(exactly = 0) { identityRepo.save(any()) }
  }

  @Test
  fun `backfills displayName when previously null`() {
    val existing = User(email = "dave@example.com", displayName = null)
    every { identityRepo.findByProviderAndSubject("oidc", "sub-4") } returns null
    every { userRepo.findByEmail("dave@example.com") } returns existing

    service.findOrCreate("oidc", "sub-4", "dave@example.com", "Dave")

    existing.displayName shouldBe "Dave"
  }
}
