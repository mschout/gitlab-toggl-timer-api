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
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException

sealed class TimeEntryDeletionException(
    val togglId: Long,
    val description: String?,
    cause: Throwable,
) : RuntimeException(cause)

class TogglTimeEntryDeletionException(togglId: Long, description: String?, cause: Throwable) :
    TimeEntryDeletionException(togglId, description, cause)

class TimeEntryHistoryDeletionException(togglId: Long, description: String?, cause: Throwable) :
    TimeEntryDeletionException(togglId, description, cause)

@Service
class TimeEntryDeletionService(
    private val timeEntryRepository: TimeEntryRepository,
    private val togglClientFactory: TogglClientFactory,
    private val credentialsService: CurrentUserCredentialsService,
) {

  fun delete(togglId: Long) {
    val userId = credentialsService.currentUserId()
    val entry =
        timeEntryRepository.findByTogglIdAndUserId(togglId = togglId, userId = userId)
            ?: throw TimeEntryNotFoundException(togglId)
    val description = entry.description?.takeIf(String::isNotBlank)

    try {
      togglClientFactory
          .forApiKey(credentialsService.requireTogglApiKey())
          .deleteTimeEntry(workspaceId = entry.workspaceId, timeEntryId = togglId)
    } catch (exception: HttpClientErrorException) {
      if (exception.statusCode != HttpStatus.NOT_FOUND) {
        throw TogglTimeEntryDeletionException(togglId, description, exception)
      }
    } catch (exception: Exception) {
      throw TogglTimeEntryDeletionException(togglId, description, exception)
    }

    try {
      timeEntryRepository.delete(entry)
    } catch (exception: Exception) {
      throw TimeEntryHistoryDeletionException(togglId, description, exception)
    }
  }
}
