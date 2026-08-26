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

import io.github.mschout.gitlab.toggltimer.project.TimeEntry
import io.github.mschout.gitlab.toggltimer.project.TimeEntryRepository
import io.github.mschout.gitlab.toggltimer.project.TogglSyncService
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.toggl.UpdateTimeEntryDescriptionRequest
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimeEntryDescriptionServiceTest {

  private val timeEntryRepository = mockk<TimeEntryRepository>()
  private val togglClientFactory = mockk<TogglClientFactory>()
  private val credentialsService = mockk<CurrentUserCredentialsService>()
  private val togglSyncService = mockk<TogglSyncService>()
  private val togglClient = mockk<TogglClient>()
  private lateinit var service: TimeEntryDescriptionService

  @BeforeEach
  fun setUp() {
    every { credentialsService.currentUserId() } returns 42L
    every { credentialsService.requireTogglApiKey() } returns "api-key"
    every { togglClientFactory.forApiKey("api-key") } returns togglClient
    service =
        TimeEntryDescriptionService(
            timeEntryRepository,
            togglClientFactory,
            credentialsService,
            togglSyncService,
        )
  }

  @Test
  fun `updates Toggl with only workspace and description then persists its full response`() {
    val local = entry(description = "Old")
    val updated = togglEntry(description = "Updated")
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns local
    every {
      togglClient.updateTimeEntryDescription(
          7L,
          123L,
          UpdateTimeEntryDescriptionRequest(workspaceId = 7L, description = "Updated"),
      )
    } returns updated
    every { togglSyncService.upsertTimeEntry(42L, updated) } returns local

    val result = service.updateDescription(togglId = 123L, description = "Updated")

    result shouldBe TimeEntryDescriptionEditorView(togglId = 123L, description = "Updated")
    verify(exactly = 1) { togglSyncService.upsertTimeEntry(42L, updated) }
  }

  @Test
  fun `blank description clears it in Toggl and Postgres`() {
    val local = entry(description = "Old")
    val updated = togglEntry(description = null)
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns local
    every {
      togglClient.updateTimeEntryDescription(
          7L,
          123L,
          UpdateTimeEntryDescriptionRequest(workspaceId = 7L, description = ""),
      )
    } returns updated
    every { togglSyncService.upsertTimeEntry(42L, updated) } returns local

    val result = service.updateDescription(togglId = 123L, description = "   ")

    result shouldBe TimeEntryDescriptionEditorView(togglId = 123L, description = null)
    verify { togglSyncService.upsertTimeEntry(42L, updated) }
  }

  @Test
  fun `unchanged description skips Toggl and Postgres`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        entry(description = "Already current")

    val result = service.updateDescription(togglId = 123L, description = "Already current")

    result shouldBe TimeEntryDescriptionEditorView(togglId = 123L, description = "Already current")
    verify(exactly = 0) { credentialsService.requireTogglApiKey() }
    verify(exactly = 0) { togglClientFactory.forApiKey(any()) }
    verify(exactly = 0) { togglSyncService.upsertTimeEntry(any(), any()) }
  }

  @Test
  fun `entry owned by another user is not accessible`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns null

    shouldThrow<TimeEntryNotFoundException> {
      service.updateDescription(togglId = 123L, description = "No access")
    }

    verify(exactly = 0) { credentialsService.requireTogglApiKey() }
    verify(exactly = 0) { togglClientFactory.forApiKey(any()) }
    verify(exactly = 0) { togglSyncService.upsertTimeEntry(any(), any()) }
  }

  @Test
  fun `Toggl failure does not update Postgres`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        entry(description = "Old")
    every { togglClient.updateTimeEntryDescription(any(), any(), any()) } throws
        RuntimeException("Toggl down")

    shouldThrow<TogglDescriptionUpdateException> {
      service.updateDescription(togglId = 123L, description = "Updated")
    }

    verify(exactly = 0) { togglSyncService.upsertTimeEntry(any(), any()) }
  }

  @Test
  fun `Postgres failure is reported after Toggl succeeds`() {
    val local = entry(description = "Old")
    val updated = togglEntry(description = "Updated")
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns local
    every { togglClient.updateTimeEntryDescription(any(), any(), any()) } returns updated
    every { togglSyncService.upsertTimeEntry(42L, updated) } throws
        RuntimeException("Postgres down")

    shouldThrow<TimeEntryHistoryUpdateException> {
      service.updateDescription(togglId = 123L, description = "Updated")
    }

    verify(exactly = 1) { togglClient.updateTimeEntryDescription(any(), any(), any()) }
  }

  private fun entry(description: String?) =
      TimeEntry(
          togglId = 123L,
          userId = 42L,
          workspaceId = 7L,
          description = description,
          start = Instant.parse("2026-08-26T12:00:00Z"),
          stop = Instant.parse("2026-08-26T13:00:00Z"),
          duration = 3_600L,
      )

  private fun togglEntry(description: String?) =
      TogglTimeEntry(
          id = 123L,
          workspaceId = 7L,
          description = description,
          start = Instant.parse("2026-08-26T12:00:00Z"),
          stop = Instant.parse("2026-08-26T13:00:00Z"),
          duration = 3_600L,
      )
}
