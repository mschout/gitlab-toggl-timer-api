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

import io.github.mschout.gitlab.toggltimer.user.User
import java.security.SecureRandom
import java.time.Instant
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

const val RECOVERY_CODE_COUNT = 10
private const val RECOVERY_CODE_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
private const val RECOVERY_CODE_LEN = 10

@Service
class MfaService(
    private val totpRepository: TotpCredentialRepository,
    private val recoveryRepository: RecoveryCodeRepository,
    private val totpService: TotpService,
    private val passwordEncoder: PasswordEncoder,
    private val userCredentialRepository: UserCredentialRepository,
    private val userEntityRepository: PublicKeyCredentialUserEntityRepository,
) {

  private val random = SecureRandom()

  /** True if the user has any confirmed TOTP or any registered passkey. */
  @Transactional(readOnly = true)
  fun hasAnyMfa(user: User): Boolean =
      totpRepository.existsByUserIdAndConfirmedTrue(user.id) || passkeysFor(user).isNotEmpty()

  @Transactional(readOnly = true)
  fun confirmedTotps(user: User): List<TotpCredential> =
      totpRepository.findByUserIdAndConfirmedTrueOrderByCreatedAtAsc(user.id)

  @Transactional(readOnly = true)
  fun passkeysFor(user: User): List<PasskeyView> {
    val entity = userEntityRepository.findByUsername(user.email) ?: return emptyList()
    return userCredentialRepository.findByUserId(entity.id).map {
      PasskeyView(
          credentialId = it.credentialId.toBase64UrlString(),
          label = it.label,
          created = it.created,
          lastUsed = it.lastUsed,
      )
    }
  }

  /** Returns a pending (unconfirmed) TOTP enrollment with a freshly generated secret. */
  @Transactional
  fun beginTotpEnrollment(user: User, label: String): TotpCredential {
    val secret = totpService.newSecret()
    val pending =
        TotpCredential(user = user, label = label.ifBlank { "Authenticator" }, secret = secret)
    return totpRepository.save(pending)
  }

  @Transactional
  fun confirmTotp(user: User, pendingId: Long, code: String): Boolean {
    val pending = totpRepository.findByIdAndUserId(pendingId, user.id) ?: return false
    if (pending.confirmed) return true
    if (!totpService.verify(pending.secret, code)) return false
    pending.confirmed = true
    pending.lastUsedAt = Instant.now()
    return true
  }

  @Transactional
  fun cancelPendingTotp(user: User, pendingId: Long) {
    val pending = totpRepository.findByIdAndUserId(pendingId, user.id) ?: return
    if (!pending.confirmed) totpRepository.delete(pending)
  }

  @Transactional
  fun removeTotp(user: User, totpId: Long): Boolean {
    val cred = totpRepository.findByIdAndUserId(totpId, user.id) ?: return false
    totpRepository.delete(cred)
    return true
  }

  @Transactional
  fun verifyTotpChallenge(user: User, code: String): Boolean {
    val devices = totpRepository.findByUserIdAndConfirmedTrueOrderByCreatedAtAsc(user.id)
    val matched = devices.firstOrNull { totpService.verify(it.secret, code) } ?: return false
    matched.lastUsedAt = Instant.now()
    return true
  }

  @Transactional
  fun verifyAndConsumeRecoveryCode(user: User, code: String): Boolean {
    val normalized = code.trim().lowercase().replace("-", "")
    if (normalized.isEmpty()) return false
    val matched =
        recoveryRepository.findByUserIdAndUsedAtIsNull(user.id).firstOrNull {
          passwordEncoder.matches(normalized, it.codeHash)
        } ?: return false
    matched.usedAt = Instant.now()
    return true
  }

  /**
   * Generates a fresh set of recovery codes, replacing any existing ones, and returns the plaintext
   * codes so the caller can show them once.
   */
  @Transactional
  fun regenerateRecoveryCodes(user: User): List<String> {
    recoveryRepository.deleteAllByUserId(user.id)
    val plaintext = List(RECOVERY_CODE_COUNT) { generateRecoveryCode() }
    plaintext.forEach { code ->
      val normalized = code.replace("-", "")
      val hash = passwordEncoder.encode(normalized) ?: error("password encoder returned null")
      recoveryRepository.save(RecoveryCode(user = user, codeHash = hash))
    }
    return plaintext
  }

  @Transactional(readOnly = true)
  fun unusedRecoveryCodeCount(user: User): Long =
      recoveryRepository.countByUserIdAndUsedAtIsNull(user.id)

  @Transactional
  fun removePasskey(user: User, credentialIdB64Url: String): Boolean {
    val entity = userEntityRepository.findByUsername(user.email) ?: return false
    val match =
        userCredentialRepository.findByUserId(entity.id).firstOrNull {
          it.credentialId.toBase64UrlString() == credentialIdB64Url
        } ?: return false
    userCredentialRepository.delete(match.credentialId)
    return true
  }

  /** True if the user has any registered passkey (regardless of TOTP). */
  @Transactional(readOnly = true)
  fun hasPasskey(user: User): Boolean = passkeysFor(user).isNotEmpty()

  private fun generateRecoveryCode(): String {
    val chars =
        CharArray(RECOVERY_CODE_LEN) {
          RECOVERY_CODE_ALPHABET[random.nextInt(RECOVERY_CODE_ALPHABET.length)]
        }
    val raw = String(chars)
    return "${raw.substring(0, 5)}-${raw.substring(5)}"
  }
}

data class PasskeyView(
    val credentialId: String,
    val label: String,
    val created: Instant?,
    val lastUsed: Instant?,
)
