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
