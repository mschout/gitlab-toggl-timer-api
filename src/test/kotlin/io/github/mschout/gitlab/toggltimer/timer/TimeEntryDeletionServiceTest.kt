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
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

class TimeEntryDeletionServiceTest {

  private val timeEntryRepository = mockk<TimeEntryRepository>()
  private val togglClientFactory = mockk<TogglClientFactory>()
  private val credentialsService = mockk<CurrentUserCredentialsService>()
  private val togglClient = mockk<TogglClient>()
  private lateinit var service: TimeEntryDeletionService

  @BeforeEach
  fun setUp() {
    every { credentialsService.currentUserId() } returns 42L
    every { credentialsService.requireTogglApiKey() } returns "api-key"
    every { togglClientFactory.forApiKey("api-key") } returns togglClient
    service = TimeEntryDeletionService(timeEntryRepository, togglClientFactory, credentialsService)
  }

  @Test
  fun `deletes from Toggl before deleting the owned Postgres entry`() {
    val entry = entry()
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry
    justRun { togglClient.deleteTimeEntry(7L, 123L) }
    justRun { timeEntryRepository.delete(entry) }

    service.delete(123L)

    verifyOrder {
      togglClient.deleteTimeEntry(workspaceId = 7L, timeEntryId = 123L)
      timeEntryRepository.delete(entry)
    }
  }

  @Test
  fun `inaccessible entry is rejected before loading credentials`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns null

    shouldThrow<TimeEntryNotFoundException> { service.delete(123L) }

    verify(exactly = 0) { credentialsService.requireTogglApiKey() }
    verify(exactly = 0) { togglClientFactory.forApiKey(any()) }
    verify(exactly = 0) { timeEntryRepository.delete(any()) }
  }

  @Test
  fun `Toggl failure leaves Postgres untouched and preserves retry context`() {
    val entry = entry()
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry
    every { togglClient.deleteTimeEntry(7L, 123L) } throws RuntimeException("Toggl down")

    val failure = shouldThrow<TogglTimeEntryDeletionException> { service.delete(123L) }

    failure.togglId shouldBe 123L
    failure.description shouldBe "Review merge request"
    verify(exactly = 0) { timeEntryRepository.delete(any()) }
  }

  @Test
  fun `Toggl not found is treated as already deleted so Postgres can be retried`() {
    val entry = entry()
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry
    every { togglClient.deleteTimeEntry(7L, 123L) } throws
        HttpClientErrorException.create(
            HttpStatus.NOT_FOUND,
            "Not Found",
            HttpHeaders.EMPTY,
            ByteArray(0),
            null,
        )
    justRun { timeEntryRepository.delete(entry) }

    service.delete(123L)

    verify(exactly = 1) { timeEntryRepository.delete(entry) }
  }

  @Test
  fun `Postgres failure is reported after Toggl succeeds`() {
    val entry = entry()
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry
    justRun { togglClient.deleteTimeEntry(7L, 123L) }
    every { timeEntryRepository.delete(entry) } throws RuntimeException("Postgres down")

    val failure = shouldThrow<TimeEntryHistoryDeletionException> { service.delete(123L) }

    failure.togglId shouldBe 123L
    failure.description shouldBe "Review merge request"
    verify(exactly = 1) { togglClient.deleteTimeEntry(7L, 123L) }
  }

  @Test
  fun `running deletion stops the matching timer before deleting it from Toggl and Postgres`() {
    val entry = entry().apply { duration = -1L }
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry
    every { togglClient.getCurrentTimeEntry() } returns
        TogglTimeEntry(id = 123L, workspaceId = 7L, duration = -1L)
    every { togglClient.stopTimeEntry(7L, 123L) } returns
        TogglTimeEntry(id = 123L, workspaceId = 7L, duration = 60L)
    justRun { togglClient.deleteTimeEntry(7L, 123L) }
    justRun { timeEntryRepository.delete(entry) }

    service.deleteRunning(123L)

    verifyOrder {
      togglClient.getCurrentTimeEntry()
      togglClient.stopTimeEntry(workspaceId = 7L, timeEntryId = 123L)
      togglClient.deleteTimeEntry(workspaceId = 7L, timeEntryId = 123L)
      timeEntryRepository.delete(entry)
    }
  }

  @Test
  fun `running deletion does not stop a replacement timer`() {
    val entry = entry().apply { duration = -1L }
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry
    every { togglClient.getCurrentTimeEntry() } returns
        TogglTimeEntry(id = 999L, workspaceId = 7L, duration = -1L)
    justRun { togglClient.deleteTimeEntry(7L, 123L) }
    justRun { timeEntryRepository.delete(entry) }

    service.deleteRunning(123L)

    verify(exactly = 0) { togglClient.stopTimeEntry(any(), any()) }
    verify(exactly = 1) { togglClient.deleteTimeEntry(7L, 123L) }
  }

  @Test
  fun `stop failure leaves Toggl entry and Postgres history untouched`() {
    val entry = entry().apply { duration = -1L }
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry
    every { togglClient.getCurrentTimeEntry() } returns
        TogglTimeEntry(id = 123L, workspaceId = 7L, duration = -1L)
    every { togglClient.stopTimeEntry(7L, 123L) } throws RuntimeException("Toggl down")

    shouldThrow<TogglTimeEntryDeletionException> { service.deleteRunning(123L) }

    verify(exactly = 0) { togglClient.deleteTimeEntry(any(), any()) }
    verify(exactly = 0) { timeEntryRepository.delete(any()) }
  }

  private fun entry() =
      TimeEntry(
          togglId = 123L,
          userId = 42L,
          workspaceId = 7L,
          description = "Review merge request",
          start = Instant.parse("2026-08-26T12:00:00Z"),
          stop = Instant.parse("2026-08-26T13:00:00Z"),
          duration = 3_600L,
      )
}
