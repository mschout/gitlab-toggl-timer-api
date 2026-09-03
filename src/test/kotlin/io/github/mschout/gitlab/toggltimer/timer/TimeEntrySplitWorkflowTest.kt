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
    verify {
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

  private fun localEntry(stop: Instant) =
      TimeEntry(
          togglId = 123L,
          userId = 42L,
          workspaceId = 7L,
          projectId = 8L,
          description = "Review issue",
          start = Instant.parse("2026-09-03T14:00:00Z"),
          stop = stop,
          duration = 3_600L,
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
