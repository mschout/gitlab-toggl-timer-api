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

import io.github.mschout.gitlab.toggltimer.project.TogglTimeEntrySyncPersistenceService
import io.github.mschout.gitlab.toggltimer.project.TogglTimeEntrySyncStateRepository
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.user.UserSettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val syncLogger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(
    prefix = "app.toggl-sync",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class TogglTimeEntrySyncJob(
    private val userSettingsRepository: UserSettingsRepository,
    private val syncStateRepository: TogglTimeEntrySyncStateRepository,
    private val togglClientFactory: TogglClientFactory,
    private val persistenceService: TogglTimeEntrySyncPersistenceService,
    private val properties: TogglTimeEntrySyncProperties,
    private val clock: Clock,
) {

  @Scheduled(
      fixedDelayString = "\${app.toggl-sync.interval:PT15M}",
      initialDelayString = "\${app.toggl-sync.initial-delay:PT30S}",
  )
  fun syncTimeEntries() {
    val syncedThrough = clock.instant()
    val settings =
        try {
          userSettingsRepository.findAllEligibleForTogglSync()
        } catch (exception: Exception) {
          syncLogger.error(exception) { "Failed to load users eligible for Toggl time entry sync" }
          return
        }

    settings.forEach { userSettings ->
      val apiKey = userSettings.togglApiKey?.takeIf { it.isNotBlank() } ?: return@forEach
      try {
        syncUser(userId = userSettings.userId, apiKey = apiKey, syncedThrough = syncedThrough)
      } catch (exception: Exception) {
        syncLogger.error(exception) {
          "Failed to sync Toggl time entries for user ${userSettings.userId}"
        }
      }
    }
  }

  private fun syncUser(userId: Long, apiKey: String, syncedThrough: Instant) {
    val previousSync =
        syncStateRepository.findById(userId).map { it.lastSuccessfulSyncAt }.orElse(null)
    val baseline = previousSync ?: syncedThrough.minus(properties.initialLookback)
    val since = baseline.minusSeconds(SINCE_OVERLAP_SECONDS).epochSecond
    val entries =
        togglClientFactory.forApiKey(apiKey).getModifiedTimeEntries(since = since, meta = true)

    check(entries.size < TOGGL_TIME_ENTRY_LIMIT) {
      "Toggl returned the $TOGGL_TIME_ENTRY_LIMIT-entry limit for user $userId; " +
          "the sync cursor was not advanced"
    }

    persistenceService.persistAndAdvance(
        userId = userId,
        entries = entries,
        syncedThrough = syncedThrough,
    )
    syncLogger.info { "Synced ${entries.size} Toggl time entries for user $userId" }
  }

  companion object {
    private const val SINCE_OVERLAP_SECONDS = 1L
    private const val TOGGL_TIME_ENTRY_LIMIT = 1_000
  }
}
