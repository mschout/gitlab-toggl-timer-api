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
import io.github.mschout.gitlab.toggltimer.toggl.UpdateTimeEntryStartRequest
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest

class TimeEntryStartServiceTest {

  private val timeEntryRepository = mockk<TimeEntryRepository>()
  private val togglClientFactory = mockk<TogglClientFactory>()
  private val credentialsService = mockk<CurrentUserCredentialsService>()
  private val togglSyncService = mockk<TogglSyncService>()
  private val togglClient = mockk<TogglClient>()
  private val clock = Clock.fixed(Instant.parse("2026-09-03T20:00:00Z"), ZoneOffset.UTC)
  private lateinit var service: TimeEntryStartService

  @BeforeEach
  fun setUp() {
    every { credentialsService.currentUserId() } returns 42L
    every { credentialsService.currentTimeZone() } returns ZoneId.of("America/Chicago")
    every { credentialsService.requireTogglApiKey() } returns "api-key"
    every { togglClientFactory.forApiKey("api-key") } returns togglClient
    service =
        TimeEntryStartService(
            timeEntryRepository,
            togglClientFactory,
            credentialsService,
            togglSyncService,
            clock,
        )
  }

  @Test
  fun `changed minute starts at the latest completed stop inside that minute`() {
    val expectedStart = Instant.parse("2026-09-03T19:30:45Z")
    val snappedStart = Instant.parse("2026-09-03T19:23:31Z")
    val local = entry(togglId = 123L, start = expectedStart, duration = -1L)
    val previous =
        entry(
            togglId = 122L,
            start = Instant.parse("2026-09-03T18:00:00Z"),
            stop = snappedStart,
            duration = 5_031L,
        )
    val current = togglEntry(togglId = 123L, start = expectedStart)
    val updated = current.copy(start = snappedStart)
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns local
    every {
      timeEntryRepository.findCompletedEndingInRange(
          userId = 42L,
          startInclusive = Instant.parse("2026-09-03T19:23:00Z"),
          endExclusive = Instant.parse("2026-09-03T19:24:00Z"),
          pageable = PageRequest.of(0, 1),
      )
    } returns listOf(previous)
    every { togglClient.getCurrentTimeEntry() } returns current
    every {
      togglClient.updateTimeEntryStart(
          workspaceId = 7L,
          timeEntryId = 123L,
          request = UpdateTimeEntryStartRequest(workspaceId = 7L, start = snappedStart),
      )
    } returns updated
    every { togglSyncService.upsertTimeEntry(42L, updated) } returns local

    val result =
        service.updateStart(
            UpdateTimeEntryStartCommand(
                togglId = 123L,
                expectedStart = expectedStart,
                startDate = LocalDate.parse("2026-09-03"),
                startTime = "2:23 PM",
            )
        )

    result shouldBe TimeEntryStartUpdateOutcome.Saved(entry = updated, historySynchronized = true)
    verify(exactly = 1) { togglSyncService.upsertTimeEntry(42L, updated) }
  }

  @Test
  fun `unchanged visible minute preserves exact seconds and skips the update`() {
    val expectedStart = Instant.parse("2026-09-03T19:30:45Z")
    val local = entry(togglId = 123L, start = expectedStart, duration = -1L)
    val current = togglEntry(togglId = 123L, start = expectedStart)
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns local
    every { togglClient.getCurrentTimeEntry() } returns current

    val result =
        service.updateStart(
            UpdateTimeEntryStartCommand(
                togglId = 123L,
                expectedStart = expectedStart,
                startDate = LocalDate.parse("2026-09-03"),
                startTime = "2:30 PM",
            )
        )

    result shouldBe TimeEntryStartUpdateOutcome.Unchanged(current)
    verify(exactly = 0) {
      timeEntryRepository.findCompletedEndingInRange(any(), any(), any(), any())
    }
    verify(exactly = 0) { togglClient.updateTimeEntryStart(any(), any(), any()) }
    verify(exactly = 0) { togglSyncService.upsertTimeEntry(any(), any()) }
  }

  @Test
  fun `changed minute defaults seconds to zero and accepts lowercase period without a space`() {
    val expectedStart = Instant.parse("2026-09-03T19:30:45Z")
    val requestedStart = Instant.parse("2026-09-03T19:23:00Z")
    val local = entry(togglId = 123L, start = expectedStart, duration = -1L)
    val current = togglEntry(togglId = 123L, start = expectedStart)
    val updated = current.copy(start = requestedStart)
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns local
    every {
      timeEntryRepository.findCompletedEndingInRange(42L, requestedStart, any(), any())
    } returns emptyList()
    every { togglClient.getCurrentTimeEntry() } returns current
    every { togglClient.updateTimeEntryStart(any(), any(), any()) } returns updated
    every { togglSyncService.upsertTimeEntry(42L, updated) } returns local

    val result =
        service.updateStart(
            UpdateTimeEntryStartCommand(
                togglId = 123L,
                expectedStart = expectedStart,
                startDate = LocalDate.parse("2026-09-03"),
                startTime = "2:23pm",
            )
        )

    result shouldBe TimeEntryStartUpdateOutcome.Saved(updated, historySynchronized = true)
    verify {
      togglClient.updateTimeEntryStart(
          7L,
          123L,
          UpdateTimeEntryStartRequest(workspaceId = 7L, start = requestedStart),
      )
    }
  }

  @Test
  fun `nonexistent daylight saving time is rejected before calling Toggl`() {
    val expectedStart = Instant.parse("2026-09-03T19:30:45Z")
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        entry(togglId = 123L, start = expectedStart, duration = -1L)

    val exception =
        shouldThrow<TimeEntryStartValidationException> {
          service.updateStart(
              UpdateTimeEntryStartCommand(
                  togglId = 123L,
                  expectedStart = expectedStart,
                  startDate = LocalDate.parse("2026-03-08"),
                  startTime = "2:30 AM",
              )
          )
        }

    exception.message shouldBe "That local time does not exist because of daylight saving time."
    verify(exactly = 0) { togglClient.getCurrentTimeEntry() }
  }

  @Test
  fun `daylight saving overlap resolves to the earlier occurrence`() {
    val expectedStart = Instant.parse("2026-09-03T19:30:45Z")
    val earlierOverlap = Instant.parse("2025-11-02T06:30:00Z")
    val local = entry(togglId = 123L, start = expectedStart, duration = -1L)
    val current = togglEntry(togglId = 123L, start = expectedStart)
    val updated = current.copy(start = earlierOverlap)
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns local
    every {
      timeEntryRepository.findCompletedEndingInRange(
          42L,
          earlierOverlap,
          earlierOverlap.plusSeconds(60),
          any(),
      )
    } returns emptyList()
    every { togglClient.getCurrentTimeEntry() } returns current
    every { togglClient.updateTimeEntryStart(any(), any(), any()) } returns updated
    every { togglSyncService.upsertTimeEntry(42L, updated) } returns local

    val result =
        service.updateStart(
            UpdateTimeEntryStartCommand(
                togglId = 123L,
                expectedStart = expectedStart,
                startDate = LocalDate.parse("2025-11-02"),
                startTime = "1:30 AM",
            )
        )

    result shouldBe TimeEntryStartUpdateOutcome.Saved(updated, historySynchronized = true)
    verify {
      togglClient.updateTimeEntryStart(
          7L,
          123L,
          UpdateTimeEntryStartRequest(workspaceId = 7L, start = earlierOverlap),
      )
    }
  }

  @Test
  fun `malformed time is rejected before calling Toggl`() {
    val expectedStart = Instant.parse("2026-09-03T19:30:45Z")
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        entry(togglId = 123L, start = expectedStart, duration = -1L)

    val exception =
        shouldThrow<TimeEntryStartValidationException> {
          service.updateStart(
              UpdateTimeEntryStartCommand(
                  togglId = 123L,
                  expectedStart = expectedStart,
                  startDate = LocalDate.parse("2026-09-03"),
                  startTime = "14:30",
              )
          )
        }

    exception.message shouldBe "Use a time such as 6:02 PM."
    verify(exactly = 0) { togglClient.getCurrentTimeEntry() }
  }

  @Test
  fun `future start is rejected before calling Toggl`() {
    val expectedStart = Instant.parse("2026-09-03T19:30:45Z")
    val futureStart = Instant.parse("2026-09-03T21:00:00Z")
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        entry(togglId = 123L, start = expectedStart, duration = -1L)
    every { timeEntryRepository.findCompletedEndingInRange(42L, futureStart, any(), any()) } returns
        emptyList()

    val exception =
        shouldThrow<TimeEntryStartValidationException> {
          service.updateStart(
              UpdateTimeEntryStartCommand(
                  togglId = 123L,
                  expectedStart = expectedStart,
                  startDate = LocalDate.parse("2026-09-03"),
                  startTime = "4:00 PM",
              )
          )
        }

    exception.message shouldBe "Start time cannot be in the future."
    verify(exactly = 0) { togglClient.getCurrentTimeEntry() }
  }

  @Test
  fun `entry owned by another user is not accessible`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns null

    shouldThrow<TimeEntryNotFoundException> {
      service.updateStart(
          UpdateTimeEntryStartCommand(
              togglId = 123L,
              expectedStart = Instant.parse("2026-09-03T19:30:45Z"),
              startDate = LocalDate.parse("2026-09-03"),
              startTime = "2:30 PM",
          )
      )
    }

    verify(exactly = 0) { togglClient.getCurrentTimeEntry() }
  }

  @Test
  fun `changed local start is reported as stale without an update`() {
    val expectedStart = Instant.parse("2026-09-03T19:30:45Z")
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        entry(togglId = 123L, start = Instant.parse("2026-09-03T19:31:00Z"), duration = -1L)
    every { togglClient.getCurrentTimeEntry() } returns
        togglEntry(togglId = 123L, start = expectedStart)

    val result =
        service.updateStart(
            UpdateTimeEntryStartCommand(
                togglId = 123L,
                expectedStart = expectedStart,
                startDate = LocalDate.parse("2026-09-03"),
                startTime = "2:30 PM",
            )
        )

    result shouldBe
        TimeEntryStartUpdateOutcome.Stale(togglEntry(togglId = 123L, start = expectedStart))
    verify(exactly = 0) { togglClient.updateTimeEntryStart(any(), any(), any()) }
  }

  @Test
  fun `replaced running timer is reported as stale without an update`() {
    val expectedStart = Instant.parse("2026-09-03T19:30:45Z")
    val replacement = togglEntry(togglId = 999L, start = Instant.parse("2026-09-03T19:50:00Z"))
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        entry(togglId = 123L, start = expectedStart, duration = -1L)
    every { togglClient.getCurrentTimeEntry() } returns replacement

    val result =
        service.updateStart(
            UpdateTimeEntryStartCommand(
                togglId = 123L,
                expectedStart = expectedStart,
                startDate = LocalDate.parse("2026-09-03"),
                startTime = "2:30 PM",
            )
        )

    result shouldBe TimeEntryStartUpdateOutcome.Stale(replacement)
    verify(exactly = 0) { togglClient.updateTimeEntryStart(any(), any(), any()) }
  }

  @Test
  fun `local history failure is reported after Toggl saves the new start`() {
    val expectedStart = Instant.parse("2026-09-03T19:30:45Z")
    val requestedStart = Instant.parse("2026-09-03T19:23:00Z")
    val local = entry(togglId = 123L, start = expectedStart, duration = -1L)
    val current = togglEntry(togglId = 123L, start = expectedStart)
    val updated = current.copy(start = requestedStart)
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns local
    every { timeEntryRepository.findCompletedEndingInRange(any(), any(), any(), any()) } returns
        emptyList()
    every { togglClient.getCurrentTimeEntry() } returns current
    every { togglClient.updateTimeEntryStart(any(), any(), any()) } returns updated
    every { togglSyncService.upsertTimeEntry(42L, updated) } throws
        RuntimeException("Postgres down")

    val result =
        service.updateStart(
            UpdateTimeEntryStartCommand(
                togglId = 123L,
                expectedStart = expectedStart,
                startDate = LocalDate.parse("2026-09-03"),
                startTime = "2:23 PM",
            )
        )

    result shouldBe TimeEntryStartUpdateOutcome.Saved(updated, historySynchronized = false)
  }

  @Test
  fun `Toggl update failure leaves local history unchanged`() {
    val expectedStart = Instant.parse("2026-09-03T19:30:45Z")
    val local = entry(togglId = 123L, start = expectedStart, duration = -1L)
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns local
    every { timeEntryRepository.findCompletedEndingInRange(any(), any(), any(), any()) } returns
        emptyList()
    every { togglClient.getCurrentTimeEntry() } returns
        togglEntry(togglId = 123L, start = expectedStart)
    every { togglClient.updateTimeEntryStart(any(), any(), any()) } throws
        RuntimeException("Toggl down")

    shouldThrow<TogglStartUpdateException> {
      service.updateStart(
          UpdateTimeEntryStartCommand(
              togglId = 123L,
              expectedStart = expectedStart,
              startDate = LocalDate.parse("2026-09-03"),
              startTime = "2:23 PM",
          )
      )
    }

    verify(exactly = 0) { togglSyncService.upsertTimeEntry(any(), any()) }
  }

  private fun entry(togglId: Long, start: Instant, stop: Instant? = null, duration: Long) =
      TimeEntry(
          togglId = togglId,
          userId = 42L,
          workspaceId = 7L,
          start = start,
          stop = stop,
          duration = duration,
      )

  private fun togglEntry(togglId: Long, start: Instant) =
      TogglTimeEntry(
          id = togglId,
          workspaceId = 7L,
          start = start,
          duration = -1L,
          description = "Working",
      )
}
