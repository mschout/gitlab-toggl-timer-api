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

import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import io.github.mschout.gitlab.toggltimer.security.AuthProperties
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

class TotpServiceTest {

  private val service =
      TotpService(
          AuthProperties(
              passwordLoginEnabled = true,
              rpName = "Test",
              rpId = "localhost",
              origins = setOf("http://localhost:8080"),
          )
      )

  @Test
  fun `verifies a freshly generated code`() {
    val secret = service.newSecret()
    val currentBucket = SystemTimeProvider().time / 30
    val code = DefaultCodeGenerator().generate(secret, currentBucket)
    service.verify(secret, code) shouldBe true
  }

  @Test
  fun `rejects malformed input`() {
    val secret = service.newSecret()
    service.verify(secret, "abcdef") shouldBe false
    service.verify(secret, "12345") shouldBe false
    service.verify(secret, "1234567") shouldBe false
  }

  @Test
  fun `qrDataUri produces a data url`() {
    val secret = service.newSecret()
    service.qrDataUri("user@example.com", secret) shouldStartWith "data:image/png;base64,"
  }
}
