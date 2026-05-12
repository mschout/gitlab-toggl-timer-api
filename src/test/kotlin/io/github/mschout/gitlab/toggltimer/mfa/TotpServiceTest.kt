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
