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

import io.github.mschout.gitlab.toggltimer.project.TimeEntryRepository
import io.github.mschout.gitlab.toggltimer.project.TogglSyncService
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TimeEntrySplitPersistenceService(
    private val operationRepository: TimeEntrySplitOperationRepository,
    private val timeEntryRepository: TimeEntryRepository,
    private val togglSyncService: TogglSyncService,
) {
  @Transactional
  fun complete(operationId: UUID, first: TogglTimeEntry, second: TogglTimeEntry) {
    val operation = operationRepository.findById(operationId).orElseThrow()
    togglSyncService.upsertTimeEntries(operation.userId, listOf(first, second))
    timeEntryRepository
        .findByTogglIdAndUserId(operation.originalTogglId, operation.userId)
        ?.let(timeEntryRepository::delete)
    operationRepository.delete(operation)
  }
}
