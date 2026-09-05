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
import io.github.mschout.gitlab.toggltimer.toggl.CreateStoppedTimeEntryRequest
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimeEntrySplitWorkflowTest {
  private val timeEntryRepository = mockk<TimeEntryRepository>()
  private val operationRepository = mockk<TimeEntrySplitOperationRepository>()
  private val clientFactory = mockk<TogglClientFactory>()
  private val credentialsService = mockk<CurrentUserCredentialsService>()
  private val persistenceService = mockk<TimeEntrySplitPersistenceService>()
  private val client = mockk<TogglClient>()
  private val now = Instant.parse("2026-09-03T15:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val workflow =
      TimeEntrySplitWorkflow(
          timeEntryRepository,
          operationRepository,
          clientFactory,
          credentialsService,
          persistenceService,
          clock,
      )

  @BeforeEach
  fun setUp() {
    every { credentialsService.currentUserId() } returns 42L
    every { credentialsService.requireTogglApiKey() } returns "api-key"
    every { clientFactory.forApiKey("api-key") } returns client
    every { operationRepository.findByUserIdAndOriginalTogglId(any(), any()) } returns null
  }

  @Test
  fun `rejects an offset that would not leave one second on each side`() {
    val entry = localEntry(stop = Instant.parse("2026-09-03T14:00:02Z"))
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry

    shouldThrow<IllegalArgumentException> {
      workflow.split(
          SplitTimeEntryCommand(
              togglId = 123L,
              expectedStart = entry.start,
              expectedStop = requireNotNull(entry.stop),
              splitOffsetSeconds = 2L,
          )
      )
    }

    verify(exactly = 0) { clientFactory.forApiKey(any()) }
  }

  @Test
  fun `prepares a running split from a fresh clock snapshot`() {
    val start = Instant.parse("2026-09-03T14:00:00Z")
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        localEntry(stop = null, duration = -start.epochSecond)
    every { client.getCurrentTimeEntry() } returns runningEntry(123L, start)

    workflow.prepareRunning(123L) shouldBe
        RunningTimeEntrySplitPreparation.Ready(
            RunningTimeEntrySplitSnapshot(togglId = 123L, start = start, snapshotEnd = now)
        )
  }

  @Test
  fun `preparing a running split rejects a changed start`() {
    val start = Instant.parse("2026-09-03T14:00:00Z")
    val changed = runningEntry(123L, start.plusSeconds(30))
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        localEntry(stop = null, duration = -start.epochSecond)
    every { client.getCurrentTimeEntry() } returns changed

    workflow.prepareRunning(123L) shouldBe
        RunningTimeEntrySplitPreparation.Rejected(
            "The running timer changed. Reopen Split from the current timer.",
            changed,
        )
  }

  @Test
  fun `resumes a recorded split and creates two exact stopped entries before deleting original`() {
    val operationId = UUID.randomUUID()
    val start = Instant.parse("2026-09-03T14:00:00Z")
    val split = Instant.parse("2026-09-03T14:30:00Z")
    val stop = Instant.parse("2026-09-03T15:00:00Z")
    val entry = localEntry(stop = stop)
    val operation =
        TimeEntrySplitOperation(
            userId = 42L,
            originalTogglId = 123L,
            workspaceId = 7L,
            projectId = 8L,
            description = "Review issue",
            originalStart = start,
            originalStop = stop,
            splitAt = split,
            billable = true,
            tags = listOf("review"),
            createdWith = "Gitlab Toggl Timer",
            id = operationId,
        )
    val original = remoteEntry(123L, start, stop, 3_600L)
    val first = remoteEntry(201L, start, split, 1_800L)
    val second = remoteEntry(202L, split, stop, 1_800L)
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry
    every { operationRepository.findByUserIdAndOriginalTogglId(42L, 123L) } returns operation
    every { operationRepository.claim(operationId, now, any()) } returns 1
    every { operationRepository.findById(operationId) } returns Optional.of(operation)
    every { operationRepository.saveAndFlush(operation) } returns operation
    every { client.getTimeEntries(any(), any(), true) } returns emptyList()
    every { client.createStoppedTimeEntry(7L, any()) } returnsMany listOf(first, second)
    every { client.getTimeEntry(123L) } returns original
    every { client.getTimeEntry(201L) } returns first
    every { client.getTimeEntry(202L) } returns second
    every { client.deleteTimeEntry(7L, 123L) } returns Unit
    every { persistenceService.complete(operationId, first, second) } returns Unit

    val outcome =
        workflow.split(
            SplitTimeEntryCommand(
                togglId = 123L,
                expectedStart = start,
                expectedStop = stop,
                splitOffsetSeconds = 1_800L,
            )
        )

    outcome shouldBe SplitTimeEntryOutcome.Completed
    verifyOrder {
      client.createStoppedTimeEntry(
          7L,
          CreateStoppedTimeEntryRequest(
              workspaceId = 7L,
              projectId = 8L,
              start = start,
              stop = split,
              description = "Review issue",
              duration = 1_800L,
              billable = true,
              tags = listOf("review"),
              createdWith = "Gitlab Toggl Timer",
          ),
      )
      client.createStoppedTimeEntry(
          7L,
          CreateStoppedTimeEntryRequest(
              workspaceId = 7L,
              projectId = 8L,
              start = split,
              stop = stop,
              description = "Review issue",
              duration = 1_800L,
              billable = true,
              tags = listOf("review"),
              createdWith = "Gitlab Toggl Timer",
          ),
      )
      client.deleteTimeEntry(7L, 123L)
      persistenceService.complete(operationId, first, second)
    }
  }

  @Test
  fun `running split creates a stopped first segment and moves the original start`() {
    val operationId = UUID.randomUUID()
    val start = Instant.parse("2026-09-03T14:00:00Z")
    val split = Instant.parse("2026-09-03T14:30:00Z")
    val snapshotEnd = Instant.parse("2026-09-03T15:00:00Z")
    val entry = localEntry(stop = null, duration = -1L)
    val operation =
        TimeEntrySplitOperation(
            userId = 42L,
            originalTogglId = 123L,
            workspaceId = 7L,
            projectId = 8L,
            description = "Review issue",
            originalStart = start,
            originalStop = null,
            splitAt = split,
            billable = true,
            tags = listOf("review"),
            createdWith = "Gitlab Toggl Timer",
            kind = TimeEntrySplitKind.RUNNING,
            id = operationId,
        )
    val original = runningEntry(123L, start)
    val first = remoteEntry(201L, start, split, 1_800L)
    val updatedOriginal = runningEntry(123L, split)
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry
    every { operationRepository.findByUserIdAndOriginalTogglId(42L, 123L) } returns null
    every { operationRepository.saveAndFlush(any()) } returns operation
    every { operationRepository.claim(operationId, now, any()) } returns 1
    every { operationRepository.findById(operationId) } returns Optional.of(operation)
    every { client.getCurrentTimeEntry() } returns original
    every { client.getTimeEntries(any(), any(), true) } returns emptyList()
    every { client.createStoppedTimeEntry(7L, any()) } returns first
    every { client.getTimeEntry(123L) } returnsMany listOf(original, updatedOriginal)
    every { client.updateTimeEntryStart(7L, 123L, any()) } returns updatedOriginal
    every { client.getTimeEntry(201L) } returns first
    every { persistenceService.completeRunning(operationId, first, updatedOriginal) } returns Unit

    val outcome =
        workflow.splitRunning(
            SplitRunningTimeEntryCommand(
                togglId = 123L,
                expectedStart = start,
                snapshotEnd = snapshotEnd,
                splitOffsetSeconds = 1_800L,
            )
        )

    outcome shouldBe SplitTimeEntryOutcome.Completed
    verifyOrder {
      client.createStoppedTimeEntry(
          7L,
          CreateStoppedTimeEntryRequest(
              workspaceId = 7L,
              projectId = 8L,
              start = start,
              stop = split,
              description = "Review issue",
              duration = 1_800L,
              billable = true,
              tags = listOf("review"),
              createdWith = "Gitlab Toggl Timer",
          ),
      )
      client.updateTimeEntryStart(
          workspaceId = 7L,
          timeEntryId = 123L,
          request =
              io.github.mschout.gitlab.toggltimer.toggl.UpdateTimeEntryStartRequest(
                  workspaceId = 7L,
                  start = split,
              ),
      )
      persistenceService.completeRunning(operationId, first, updatedOriginal)
    }
  }

  @Test
  fun `stopping before the start update removes the first segment and preserves the original`() {
    val operationId = UUID.randomUUID()
    val start = Instant.parse("2026-09-03T14:00:00Z")
    val split = Instant.parse("2026-09-03T14:30:00Z")
    val stopped = remoteEntry(123L, start, now, 3_600L)
    val operation =
        runningOperation(operationId, start, split).also {
          it.firstChildTogglId = 201L
          it.phase = TimeEntrySplitPhase.UPDATING_ORIGINAL_START
        }
    every { operationRepository.claim(operationId, now, any()) } returns 1
    every { operationRepository.findById(operationId) } returns Optional.of(operation)
    every { operationRepository.saveAndFlush(operation) } returns operation
    every { operationRepository.delete(operation) } returns Unit
    every { client.getTimeEntry(123L) } returns stopped
    every { client.deleteTimeEntry(7L, 201L) } returns Unit

    val outcome = workflow.resume(operationId, "api-key")

    outcome shouldBe
        SplitTimeEntryOutcome.Rejected(
            "The timer was stopped before the split could be applied. The original was preserved."
        )
    verify(exactly = 0) { client.updateTimeEntryStart(any(), any(), any()) }
    verifyOrder {
      client.getTimeEntry(123L)
      client.deleteTimeEntry(7L, 201L)
      operationRepository.delete(operation)
    }
  }

  @Test
  fun `stopping after the start update completes with two stopped entries`() {
    val operationId = UUID.randomUUID()
    val start = Instant.parse("2026-09-03T14:00:00Z")
    val split = Instant.parse("2026-09-03T14:30:00Z")
    val operation =
        runningOperation(operationId, start, split).also {
          it.firstChildTogglId = 201L
          it.phase = TimeEntrySplitPhase.ORIGINAL_START_UPDATED
        }
    val first = remoteEntry(201L, start, split, 1_800L)
    val stoppedOriginal = remoteEntry(123L, split, now, 1_800L)
    every { operationRepository.claim(operationId, now, any()) } returns 1
    every { operationRepository.findById(operationId) } returns Optional.of(operation)
    every { client.getTimeEntry(201L) } returns first
    every { client.getTimeEntry(123L) } returns stoppedOriginal
    every { persistenceService.completeRunning(operationId, first, stoppedOriginal) } returns Unit

    workflow.resume(operationId, "api-key") shouldBe SplitTimeEntryOutcome.Completed

    verify { persistenceService.completeRunning(operationId, first, stoppedOriginal) }
  }

  @Test
  fun `retry reconciles an ambiguous original start update without updating twice`() {
    val operationId = UUID.randomUUID()
    val start = Instant.parse("2026-09-03T14:00:00Z")
    val split = Instant.parse("2026-09-03T14:30:00Z")
    val operation =
        runningOperation(operationId, start, split).also {
          it.firstChildTogglId = 201L
          it.phase = TimeEntrySplitPhase.UPDATING_ORIGINAL_START
        }
    val first = remoteEntry(201L, start, split, 1_800L)
    val updatedOriginal = runningEntry(123L, split)
    every { operationRepository.claim(operationId, now, any()) } returns 1
    every { operationRepository.findById(operationId) } returns Optional.of(operation)
    every { operationRepository.saveAndFlush(operation) } returns operation
    every { client.getTimeEntry(123L) } returnsMany listOf(updatedOriginal, updatedOriginal)
    every { client.getTimeEntry(201L) } returns first
    every { persistenceService.completeRunning(operationId, first, updatedOriginal) } returns Unit

    workflow.resume(operationId, "api-key") shouldBe SplitTimeEntryOutcome.Completed

    verify(exactly = 0) { client.updateTimeEntryStart(any(), any(), any()) }
  }

  @Test
  fun `unexpected original start enters needs review without deleting tracked time`() {
    val operationId = UUID.randomUUID()
    val start = Instant.parse("2026-09-03T14:00:00Z")
    val split = Instant.parse("2026-09-03T14:30:00Z")
    val operation =
        runningOperation(operationId, start, split).also {
          it.firstChildTogglId = 201L
          it.phase = TimeEntrySplitPhase.UPDATING_ORIGINAL_START
        }
    every { operationRepository.claim(operationId, now, any()) } returns 1
    every { operationRepository.findById(operationId) } returns Optional.of(operation)
    every { operationRepository.saveAndFlush(operation) } returns operation
    every { client.getTimeEntry(123L) } returns runningEntry(123L, start.plusSeconds(30))

    workflow.resume(operationId, "api-key") shouldBe
        SplitTimeEntryOutcome.NeedsReview(
            operationId,
            "The running timer start changed unexpectedly in Toggl.",
        )

    operation.phase shouldBe TimeEntrySplitPhase.NEEDS_REVIEW
    verify(exactly = 0) { client.deleteTimeEntry(any(), any()) }
  }

  private fun runningOperation(operationId: UUID, start: Instant, split: Instant) =
      TimeEntrySplitOperation(
          userId = 42L,
          originalTogglId = 123L,
          workspaceId = 7L,
          projectId = 8L,
          description = "Review issue",
          originalStart = start,
          originalStop = null,
          splitAt = split,
          billable = true,
          tags = listOf("review"),
          createdWith = "Gitlab Toggl Timer",
          kind = TimeEntrySplitKind.RUNNING,
          id = operationId,
      )

  private fun localEntry(stop: Instant?, duration: Long = 3_600L) =
      TimeEntry(
          togglId = 123L,
          userId = 42L,
          workspaceId = 7L,
          projectId = 8L,
          description = "Review issue",
          start = Instant.parse("2026-09-03T14:00:00Z"),
          stop = stop,
          duration = duration,
          billable = true,
          tags = listOf("review"),
          createdWith = "Gitlab Toggl Timer",
      )

  private fun runningEntry(id: Long, start: Instant) =
      TogglTimeEntry(
          id = id,
          workspaceId = 7L,
          projectId = 8L,
          start = start,
          description = "Review issue",
          duration = -start.epochSecond,
          billable = true,
          tags = listOf("review"),
          createdWith = "Gitlab Toggl Timer",
      )

  private fun remoteEntry(id: Long, start: Instant, stop: Instant, duration: Long) =
      TogglTimeEntry(
          id = id,
          workspaceId = 7L,
          projectId = 8L,
          start = start,
          stop = stop,
          description = "Review issue",
          duration = duration,
          billable = true,
          tags = listOf("review"),
          createdWith = "Gitlab Toggl Timer",
      )
}
