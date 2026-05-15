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
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.qr.QrData
import dev.samstevens.totp.qr.ZxingPngQrGenerator
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import io.github.mschout.gitlab.toggltimer.security.AuthProperties
import java.util.Base64
import org.springframework.stereotype.Service

@Service
class TotpService(private val authProperties: AuthProperties) {

  private val secretGenerator = DefaultSecretGenerator()
  private val codeVerifier =
      DefaultCodeVerifier(DefaultCodeGenerator(), SystemTimeProvider()).apply {
        setTimePeriod(30)
        setAllowedTimePeriodDiscrepancy(1)
      }
  private val qrGenerator = ZxingPngQrGenerator()

  fun newSecret(): String = secretGenerator.generate()

  fun provisioningUri(accountLabel: String, secret: String): String =
      QrData.Builder()
          .label(accountLabel)
          .secret(secret)
          .issuer(authProperties.rpName)
          .algorithm(dev.samstevens.totp.code.HashingAlgorithm.SHA1)
          .digits(6)
          .period(30)
          .build()
          .uri

  /** Returns a data: URI suitable for an `<img src>` element. */
  fun qrDataUri(accountLabel: String, secret: String): String {
    val data =
        QrData.Builder()
            .label(accountLabel)
            .secret(secret)
            .issuer(authProperties.rpName)
            .algorithm(dev.samstevens.totp.code.HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build()
    val png = qrGenerator.generate(data)
    val encoded = Base64.getEncoder().encodeToString(png)
    return "data:${qrGenerator.imageMimeType};base64,$encoded"
  }

  fun verify(secret: String, code: String): Boolean {
    val normalized = code.trim().replace(" ", "")
    if (normalized.length != 6 || !normalized.all { it.isDigit() }) return false
    return codeVerifier.isValidCode(secret, normalized)
  }
}
