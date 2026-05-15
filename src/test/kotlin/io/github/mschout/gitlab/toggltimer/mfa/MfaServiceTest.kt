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
package io.github.mschout.gitlab.toggltimer.mfa

import io.github.mschout.gitlab.toggltimer.security.AuthProperties
import io.github.mschout.gitlab.toggltimer.user.User
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository

class MfaServiceTest {

  private val totpRepo = mockk<TotpCredentialRepository>(relaxed = true)
  private val recoveryRepo = mockk<RecoveryCodeRepository>(relaxed = true)
  private val totpService =
      TotpService(
          AuthProperties(
              passwordLoginEnabled = true,
              rpName = "Test",
              rpId = "localhost",
              origins = setOf("http://localhost:8080"),
          )
      )
  private val passwordEncoder = BCryptPasswordEncoder()
  private val userCredentialRepo = mockk<UserCredentialRepository>(relaxed = true)
  private val userEntityRepo = mockk<PublicKeyCredentialUserEntityRepository>(relaxed = true)
  private val service =
      MfaService(
          totpRepo,
          recoveryRepo,
          totpService,
          passwordEncoder,
          userCredentialRepo,
          userEntityRepo,
      )
  private val user = User(email = "alice@example.com", id = 42L)

  @Test
  fun `hasAnyMfa is false when nothing is enrolled`() {
    every { totpRepo.existsByUserIdAndConfirmedTrue(42L) } returns false
    every { userEntityRepo.findByUsername("alice@example.com") } returns null
    service.hasAnyMfa(user) shouldBe false
  }

  @Test
  fun `regenerateRecoveryCodes returns ten plaintext codes and hashes them`() {
    val captured = mutableListOf<RecoveryCode>()
    every { recoveryRepo.save(any()) } answers
        {
          val rc = firstArg<RecoveryCode>()
          captured += rc
          rc
        }

    val codes = service.regenerateRecoveryCodes(user)
    codes shouldHaveSize RECOVERY_CODE_COUNT
    codes.all { it.matches(Regex("[a-z0-9]{5}-[a-z0-9]{5}")) } shouldBe true
    captured shouldHaveSize RECOVERY_CODE_COUNT
    captured.zip(codes).forEach { (saved, plain) ->
      passwordEncoder.matches(plain.replace("-", ""), saved.codeHash) shouldBe true
    }
    verify { recoveryRepo.deleteAllByUserId(42L) }
  }

  @Test
  fun `verifyAndConsumeRecoveryCode marks the matching code used and rejects replay`() {
    val plain = "abcde-fghij"
    val hash = passwordEncoder.encode(plain.replace("-", "")) ?: error("encode null")
    val rc = RecoveryCode(user = user, codeHash = hash)
    every { recoveryRepo.findByUserIdAndUsedAtIsNull(42L) } returns listOf(rc) andThen emptyList()

    service.verifyAndConsumeRecoveryCode(user, plain) shouldBe true
    (rc.usedAt != null) shouldBe true

    service.verifyAndConsumeRecoveryCode(user, plain) shouldBe false
  }
}
