package io.github.mschout.gitlab.toggltimer.mfa

import org.springframework.data.jpa.repository.JpaRepository

interface TotpCredentialRepository : JpaRepository<TotpCredential, Long> {
  fun findByUserIdAndConfirmedTrueOrderByCreatedAtAsc(userId: Long): List<TotpCredential>

  fun findByIdAndUserId(id: Long, userId: Long): TotpCredential?

  fun existsByUserIdAndConfirmedTrue(userId: Long): Boolean

  fun countByUserIdAndConfirmedTrue(userId: Long): Long
}
