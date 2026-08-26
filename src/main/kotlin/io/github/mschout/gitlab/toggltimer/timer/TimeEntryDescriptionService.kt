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
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.UpdateTimeEntryDescriptionRequest
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus

data class TimeEntryDescriptionEditorView(
    val togglId: Long,
    val description: String?,
    val error: String? = null,
    val editing: Boolean = false,
)

@ResponseStatus(HttpStatus.NOT_FOUND)
class TimeEntryNotFoundException(togglId: Long) :
    RuntimeException("Time entry $togglId was not found")

class TogglDescriptionUpdateException(cause: Throwable) :
    RuntimeException("Toggl rejected the description update", cause)

class TimeEntryHistoryUpdateException(cause: Throwable) :
    RuntimeException("Toggl was updated but local history could not be updated", cause)

@Service
class TimeEntryDescriptionService(
    private val timeEntryRepository: TimeEntryRepository,
    private val togglClientFactory: TogglClientFactory,
    private val credentialsService: CurrentUserCredentialsService,
    private val togglSyncService: TogglSyncService,
) {

  fun updateDescription(togglId: Long, description: String): TimeEntryDescriptionEditorView {
    val userId = credentialsService.currentUserId()
    val entry =
        timeEntryRepository.findByTogglIdAndUserId(togglId = togglId, userId = userId)
            ?: throw TimeEntryNotFoundException(togglId)
    val normalizedDescription = description.takeIf { it.isNotBlank() }
    val currentDescription = entry.description?.takeIf { it.isNotBlank() }

    if (normalizedDescription == currentDescription) {
      return TimeEntryDescriptionEditorView(togglId = togglId, description = currentDescription)
    }

    val request =
        UpdateTimeEntryDescriptionRequest(
            workspaceId = entry.workspaceId,
            description = normalizedDescription.orEmpty(),
        )
    val updatedEntry =
        try {
          togglClientFactory
              .forApiKey(credentialsService.requireTogglApiKey())
              .updateTimeEntryDescription(entry.workspaceId, togglId, request)
        } catch (ex: Exception) {
          throw TogglDescriptionUpdateException(ex)
        }

    try {
      togglSyncService.upsertTimeEntry(userId, updatedEntry)
    } catch (ex: Exception) {
      throw TimeEntryHistoryUpdateException(ex)
    }

    return TimeEntryDescriptionEditorView(
        togglId = togglId,
        description = updatedEntry.description?.takeIf { it.isNotBlank() },
    )
  }
}
