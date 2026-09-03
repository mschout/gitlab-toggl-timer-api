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
package io.github.mschout.gitlab.toggltimer.timer

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

enum class TimeEntrySplitPhase {
  READY,
  CREATING_FIRST,
  FIRST_CREATED,
  CREATING_SECOND,
  CHILDREN_CREATED,
  DELETING_ORIGINAL,
  ORIGINAL_DELETED,
  CLEANING_SECOND,
  CLEANING_FIRST,
  NEEDS_REVIEW,
}

@Entity
@Table(name = "time_entry_split_operations")
class TimeEntrySplitOperation(
    // spotless:off
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(name = "original_toggl_id", nullable = false, updatable = false)
    val originalTogglId: Long,

    @Column(name = "workspace_id", nullable = false, updatable = false)
    val workspaceId: Long,

    @Column(name = "project_id", updatable = false)
    val projectId: Long? = null,

    @Column(name = "task_id", updatable = false)
    val taskId: Long? = null,

    @Column(name = "description", columnDefinition = "TEXT", updatable = false)
    val description: String? = null,

    @Column(name = "original_start", nullable = false, updatable = false)
    val originalStart: Instant,

    @Column(name = "original_stop", nullable = false, updatable = false)
    val originalStop: Instant,

    @Column(name = "split_at", nullable = false, updatable = false)
    val splitAt: Instant,

    @Column(name = "billable", nullable = false, updatable = false)
    val billable: Boolean,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false, columnDefinition = "jsonb", updatable = false)
    val tags: List<String>,

    @Column(name = "created_with", nullable = false, updatable = false)
    val createdWith: String,

    @Column(name = "first_child_toggl_id")
    var firstChildTogglId: Long? = null,

    @Column(name = "second_child_toggl_id")
    var secondChildTogglId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false)
    var phase: TimeEntrySplitPhase = TimeEntrySplitPhase.READY,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Instant = Instant.now(),

    @Column(name = "lease_until")
    var leaseUntil: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID? = null,
    // spotless:on
) {
  @PrePersist
  @PreUpdate
  fun touchUpdatedAt() {
    updatedAt = Instant.now()
  }
}
