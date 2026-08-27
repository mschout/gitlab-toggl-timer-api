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

import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TogglTimeEntrySyncPersistenceService(
    private val togglSyncService: TogglSyncService,
    private val syncStateRepository: TogglTimeEntrySyncStateRepository,
) {

  @Transactional
  fun persistAndAdvance(userId: Long, entries: List<TogglTimeEntry>, syncedThrough: Instant) {
    togglSyncService.upsertTimeEntries(userId = userId, entries = entries)

    val state =
        syncStateRepository.findById(userId).orElseGet {
          TogglTimeEntrySyncState(userId = userId, lastSuccessfulSyncAt = syncedThrough)
        }
    state.lastSuccessfulSyncAt = syncedThrough
    syncStateRepository.save(state)
  }
}
