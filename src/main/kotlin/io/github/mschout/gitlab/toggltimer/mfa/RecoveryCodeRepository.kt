package io.github.mschout.gitlab.toggltimer.mfa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RecoveryCodeRepository : JpaRepository<RecoveryCode, Long> {
  fun findByUserIdAndUsedAtIsNull(userId: Long): List<RecoveryCode>

  fun countByUserIdAndUsedAtIsNull(userId: Long): Long

  @Modifying
  @Query("DELETE FROM RecoveryCode r WHERE r.user.id = :userId")
  fun deleteAllByUserId(userId: Long)
}
