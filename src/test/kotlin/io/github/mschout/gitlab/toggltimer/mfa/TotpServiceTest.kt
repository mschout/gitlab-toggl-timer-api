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

import com.helger.totp.code.DefaultCodeGenerator
import com.helger.totp.code.DefaultCodeVerifier
import com.helger.totp.code.EHashingAlgorithm
import com.helger.totp.time.ITimeProvider
import com.helger.totp.time.SystemTimeProvider
import io.github.mschout.gitlab.toggltimer.security.AuthProperties
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

class TotpServiceTest {

  private companion object {
    const val RFC_SHA1_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
  }

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
  fun `matches the RFC 6238 SHA1 test vector`() {
    val codeGenerator = DefaultCodeGenerator(EHashingAlgorithm.SHA1, 8)
    codeGenerator.generate(RFC_SHA1_SECRET, 59 / 30) shouldBe "94287082"
  }

  @Test
  fun `accepts one adjacent time period but rejects two`() {
    val codeGenerator = DefaultCodeGenerator()
    val verifier =
        DefaultCodeVerifier(codeGenerator, ITimeProvider { 60 }).apply {
          setTimePeriod(30)
          setAllowedTimePeriodDiscrepancy(1)
        }

    verifier.isValidCode(RFC_SHA1_SECRET, codeGenerator.generate(RFC_SHA1_SECRET, 1)) shouldBe true
    verifier.isValidCode(RFC_SHA1_SECRET, codeGenerator.generate(RFC_SHA1_SECRET, 2)) shouldBe true
    verifier.isValidCode(RFC_SHA1_SECRET, codeGenerator.generate(RFC_SHA1_SECRET, 3)) shouldBe true
    verifier.isValidCode(RFC_SHA1_SECRET, codeGenerator.generate(RFC_SHA1_SECRET, 0)) shouldBe false
    verifier.isValidCode(RFC_SHA1_SECRET, codeGenerator.generate(RFC_SHA1_SECRET, 4)) shouldBe false
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
