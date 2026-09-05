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
import io.github.mschout.gitlab.toggltimer.support.PostgresContainerSupport
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.user.User
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class TimeEntrySplitPersistenceServiceIT
@Autowired
constructor(
    private val persistenceService: TimeEntrySplitPersistenceService,
    private val operationRepository: TimeEntrySplitOperationRepository,
    private val timeEntryRepository: TimeEntryRepository,
    private val userRepository: UserRepository,
) : PostgresContainerSupport() {

  @AfterEach
  fun cleanUp() {
    operationRepository.deleteAll()
    timeEntryRepository.deleteAll()
    userRepository.deleteAll()
  }

  @Test
  fun `completes a running split without replacing the original row`() {
    val user = userRepository.save(User(email = "running-split@example.com"))
    val start = Instant.parse("2026-09-05T13:00:00Z")
    val splitAt = Instant.parse("2026-09-05T13:30:00Z")
    val original =
        timeEntryRepository.save(
            TimeEntry(
                togglId = 123L,
                userId = user.id,
                workspaceId = 7L,
                description = "Before split",
                start = start,
                duration = -start.epochSecond,
            )
        )
    val originalRowId = original.id
    val operation =
        operationRepository.save(
            TimeEntrySplitOperation(
                userId = user.id,
                originalTogglId = 123L,
                workspaceId = 7L,
                description = "Latest metadata",
                originalStart = start,
                splitAt = splitAt,
                billable = false,
                tags = emptyList(),
                createdWith = "Gitlab Toggl Timer",
                kind = TimeEntrySplitKind.RUNNING,
            )
        )
    val first = stoppedEntry(id = 201L, start = start, stop = splitAt)
    val updatedOriginal =
        TogglTimeEntry(
            id = 123L,
            workspaceId = 7L,
            description = "Latest metadata",
            start = splitAt,
            duration = -splitAt.epochSecond,
        )

    persistenceService.completeRunning(requireNotNull(operation.id), first, updatedOriginal)

    val persistedOriginal = timeEntryRepository.findByTogglIdAndUserId(123L, user.id)
    persistedOriginal?.id shouldBe originalRowId
    persistedOriginal?.start shouldBe splitAt
    persistedOriginal?.stop shouldBe null
    persistedOriginal?.description shouldBe "Latest metadata"
    timeEntryRepository.findAll().map { it.togglId }.shouldContainExactlyInAnyOrder(123L, 201L)
    operationRepository.findById(requireNotNull(operation.id)).isEmpty shouldBe true
  }

  private fun stoppedEntry(id: Long, start: Instant, stop: Instant) =
      TogglTimeEntry(
          id = id,
          workspaceId = 7L,
          description = "Latest metadata",
          start = start,
          stop = stop,
          duration = stop.epochSecond - start.epochSecond,
      )
}
