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
package io.github.mschout.gitlab.toggltimer.user

import io.github.mschout.gitlab.toggltimer.security.EncryptedStringConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_settings")
class UserSettings(
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    var user: User,
    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "gitlab_access_token_encrypted")
    var gitlabAccessToken: String? = null,
    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "toggl_api_key_encrypted")
    var togglApiKey: String? = null,
    @Column(name = "toggl_workspace_id") var togglWorkspaceId: Long? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @Id @Column(name = "user_id") var userId: Long = 0,
) {
  @PrePersist
  @PreUpdate
  fun touchUpdatedAt() {
    updatedAt = Instant.now()
  }
}
