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
package io.github.mschout.gitlab.toggltimer.project

import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TimeEntryRepository : JpaRepository<TimeEntry, UUID> {
  fun findByTogglId(togglId: Long): TimeEntry?

  fun findByTogglIdAndUserId(togglId: Long, userId: Long): TimeEntry?

  fun findAllByTogglIdIn(togglIds: Collection<Long>): List<TimeEntry>

  @Query(
      """
      SELECT entry
      FROM TimeEntry entry
      WHERE entry.userId = :userId
        AND entry.start >= :startInclusive
        AND entry.start < :endExclusive
        AND entry.stop IS NOT NULL
        AND entry.duration >= 0
        AND entry.serverDeletedAt IS NULL
      ORDER BY entry.start DESC
      """
  )
  fun findCompletedInRange(
      @Param("userId") userId: Long,
      @Param("startInclusive") startInclusive: Instant,
      @Param("endExclusive") endExclusive: Instant,
  ): List<TimeEntry>

  @Query(
      """
      SELECT entry
      FROM TimeEntry entry
      WHERE entry.userId = :userId
        AND entry.stop >= :startInclusive
        AND entry.stop < :endExclusive
        AND entry.duration >= 0
        AND entry.serverDeletedAt IS NULL
      ORDER BY entry.stop DESC
      """
  )
  fun findCompletedEndingInRange(
      @Param("userId") userId: Long,
      @Param("startInclusive") startInclusive: Instant,
      @Param("endExclusive") endExclusive: Instant,
      pageable: Pageable,
  ): List<TimeEntry>

  @Query(
      """
      SELECT CASE WHEN COUNT(entry) > 0 THEN true ELSE false END
      FROM TimeEntry entry
      WHERE entry.userId = :userId
        AND entry.start < :before
        AND entry.stop IS NOT NULL
        AND entry.duration >= 0
        AND entry.serverDeletedAt IS NULL
      """
  )
  fun existsCompletedBefore(
      @Param("userId") userId: Long,
      @Param("before") before: Instant,
  ): Boolean
}
