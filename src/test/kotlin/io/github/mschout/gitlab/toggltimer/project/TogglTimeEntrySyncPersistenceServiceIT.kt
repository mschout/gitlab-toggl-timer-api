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
package io.github.mschout.gitlab.toggltimer.project

import io.github.mschout.gitlab.toggltimer.support.PostgresContainerSupport
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.user.User
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class TogglTimeEntrySyncPersistenceServiceIT
@Autowired
constructor(
    private val persistenceService: TogglTimeEntrySyncPersistenceService,
    private val syncStateRepository: TogglTimeEntrySyncStateRepository,
    private val timeEntryRepository: TimeEntryRepository,
    private val projectRepository: ProjectRepository,
    private val clientRepository: ClientRepository,
    private val userRepository: UserRepository,
) : PostgresContainerSupport() {

  @AfterEach
  fun cleanUp() {
    timeEntryRepository.deleteAll()
    syncStateRepository.deleteAll()
    projectRepository.deleteAll()
    clientRepository.deleteAll()
    userRepository.deleteAll()
  }

  @Test
  fun `persists entry metadata and advances the cursor together`() {
    val user = userRepository.save(User(email = "scheduled-sync@example.com"))
    val syncedThrough = Instant.parse("2026-08-27T18:00:00Z")

    persistenceService.persistAndAdvance(
        userId = user.id,
        entries =
            listOf(
                TogglTimeEntry(
                    id = 123L,
                    workspaceId = 7L,
                    projectId = 100L,
                    projectName = "74393 - Indiana",
                    projectColor = "#4c6ef5",
                    projectActive = true,
                    clientId = 200L,
                    clientName = "Inforuptcy",
                    start = Instant.parse("2026-08-27T16:00:00Z"),
                    stop = Instant.parse("2026-08-27T17:00:00Z"),
                    duration = 3600L,
                )
            ),
        syncedThrough = syncedThrough,
    )

    val entry = timeEntryRepository.findByTogglIdAndUserId(123L, user.id)
    entry.shouldNotBeNull()
    entry.description shouldBe null
    projectRepository.findByTogglId(100L)?.name shouldBe "74393 - Indiana"
    clientRepository.findByTogglId(200L)?.name shouldBe "Inforuptcy"
    syncStateRepository.findById(user.id).orElseThrow().lastSuccessfulSyncAt shouldBe syncedThrough
  }

  @Test
  fun `rolls back entries and cursor when an entry cannot be persisted`() {
    val user = userRepository.save(User(email = "scheduled-sync-rollback@example.com"))
    val start = Instant.parse("2026-08-27T16:00:00Z")

    shouldThrow<IllegalArgumentException> {
      persistenceService.persistAndAdvance(
          userId = user.id,
          entries =
              listOf(
                  TogglTimeEntry(id = 123L, workspaceId = 7L, start = start, duration = 60L),
                  TogglTimeEntry(id = null, workspaceId = 7L, start = start, duration = 60L),
              ),
          syncedThrough = Instant.parse("2026-08-27T18:00:00Z"),
      )
    }

    timeEntryRepository.findAll().shouldBeEmpty()
    syncStateRepository.findById(user.id).isEmpty shouldBe true
  }
}
