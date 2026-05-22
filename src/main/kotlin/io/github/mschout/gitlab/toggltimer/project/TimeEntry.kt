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

import jakarta.persistence.Column
import jakarta.persistence.Entity
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

@Entity
@Table(name = "time_entries")
class TimeEntry(
    @Column(name = "toggl_id", nullable = false, unique = true, updatable = false)
    val togglId: Long,
    @Column(name = "user_id", nullable = false, updatable = false) val userId: Long,
    @Column(name = "toggl_user_id") var togglUserId: Long? = null,
    @Column(name = "workspace_id", nullable = false) var workspaceId: Long,
    @Column(name = "project_id") var projectId: Long? = null,
    @Column(name = "task_id") var taskId: Long? = null,
    @Column(name = "description", columnDefinition = "TEXT") var description: String? = null,
    @Column(name = "start", nullable = false) var start: Instant,
    @Column(name = "stop") var stop: Instant? = null,
    @Column(name = "duration", nullable = false) var duration: Long,
    @Column(name = "billable", nullable = false) var billable: Boolean = false,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false, columnDefinition = "jsonb")
    var tags: List<String> = emptyList(),
    @Column(name = "created_with") var createdWith: String? = null,
    @Column(name = "toggl_at") var togglAt: Instant? = null,
    @Column(name = "server_deleted_at") var serverDeletedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID? = null,
) {
  @PrePersist
  @PreUpdate
  fun touchUpdatedAt() {
    updatedAt = Instant.now()
  }
}
