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
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspaceClient
import io.github.mschout.gitlab.toggltimer.user.User
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class TogglSyncServiceIT
@Autowired
constructor(
    private val syncService: TogglSyncService,
    private val workspaceRepository: WorkspaceRepository,
    private val clientRepository: ClientRepository,
    private val projectRepository: ProjectRepository,
    private val timeEntryRepository: TimeEntryRepository,
    private val userRepository: UserRepository,
) : PostgresContainerSupport() {

  @AfterEach
  fun cleanUp() {
    timeEntryRepository.deleteAll()
    projectRepository.deleteAll()
    clientRepository.deleteAll()
    workspaceRepository.deleteAll()
    userRepository.deleteAll()
  }

  @Test
  fun `upsertWorkspaces inserts new rows and is idempotent on re-sync`() {
    syncService.upsertWorkspaces(
        listOf(TogglWorkspace(id = 1L, name = "Alpha"), TogglWorkspace(id = 2L, name = "Beta"))
    )

    workspaceRepository.findAll() shouldHaveSize 2

    val first = workspaceRepository.findByTogglId(1L)
    first.shouldNotBeNull()
    val initialCreatedAt = first.createdAt
    val initialId = first.id

    syncService.upsertWorkspaces(
        listOf(
            TogglWorkspace(id = 1L, name = "Alpha Renamed"),
            TogglWorkspace(id = 2L, name = "Beta"),
        )
    )

    workspaceRepository.findAll() shouldHaveSize 2
    val reloaded = workspaceRepository.findByTogglId(1L)
    reloaded.shouldNotBeNull()
    reloaded.name shouldBe "Alpha Renamed"
    reloaded.createdAt shouldBe initialCreatedAt
    reloaded.id shouldBe initialId
  }

  @Test
  fun `upsertClients inserts new rows and is idempotent on re-sync`() {
    syncService.upsertClients(
        7L,
        listOf(
            TogglWorkspaceClient(id = 10L, name = "Globex"),
            TogglWorkspaceClient(id = 11L, name = "Initech"),
        ),
    )

    clientRepository.findAll() shouldHaveSize 2

    val first = clientRepository.findByTogglId(10L)
    first.shouldNotBeNull()
    val initialCreatedAt = first.createdAt

    syncService.upsertClients(
        7L,
        listOf(
            TogglWorkspaceClient(id = 10L, name = "Globex Renamed"),
            TogglWorkspaceClient(id = 11L, name = "Initech"),
        ),
    )

    clientRepository.findAll() shouldHaveSize 2
    val reloaded = clientRepository.findByTogglId(10L)
    reloaded.shouldNotBeNull()
    reloaded.name shouldBe "Globex Renamed"
    reloaded.createdAt shouldBe initialCreatedAt
  }

  @Test
  fun `upsertProject inserts a row and updates it on re-sync`() {
    syncService.upsertProject(
        7L,
        TogglProject(
            id = 999L,
            name = "42 - First",
            clientId = 10L,
            color = "#ef4444",
            active = true,
        ),
    )

    val first = projectRepository.findByTogglId(999L)
    first.shouldNotBeNull()
    first.workspaceId shouldBe 7L
    first.togglClientId shouldBe 10L
    first.name shouldBe "42 - First"
    first.color shouldBe "#ef4444"
    first.active shouldBe true
    val initialCreatedAt = first.createdAt

    syncService.upsertProject(
        7L,
        TogglProject(
            id = 999L,
            name = "42 - Renamed",
            clientId = 11L,
            color = "#10b981",
            active = false,
        ),
    )

    projectRepository.findAll() shouldHaveSize 1
    val reloaded = projectRepository.findByTogglId(999L)
    reloaded.shouldNotBeNull()
    reloaded.name shouldBe "42 - Renamed"
    reloaded.togglClientId shouldBe 11L
    reloaded.color shouldBe "#10b981"
    reloaded.active shouldBe false
    reloaded.createdAt shouldBe initialCreatedAt
  }

  @Test
  fun `upsertTimeEntry inserts a row, updates it on re-sync, and round-trips JSONB tags`() {
    val user = userRepository.save(User(email = "ts-${System.nanoTime()}@example.com"))

    val start = Instant.parse("2026-05-22T12:00:00Z")
    val stop = Instant.parse("2026-05-22T13:00:00Z")

    syncService.upsertTimeEntry(
        userId = user.id,
        entry =
            TogglTimeEntry(
                id = 12345L,
                workspaceId = 7L,
                projectId = 100L,
                start = start,
                stop = stop,
                description = "first pass",
                duration = 3600L,
                billable = true,
                tags = listOf("dev", "urgent"),
            ),
    )

    val first = timeEntryRepository.findByTogglId(12345L)
    first.shouldNotBeNull()
    first.userId shouldBe user.id
    first.workspaceId shouldBe 7L
    first.projectId shouldBe 100L
    first.description shouldBe "first pass"
    first.start shouldBe start
    first.stop shouldBe stop
    first.duration shouldBe 3600L
    first.billable shouldBe true
    first.tags shouldBe listOf("dev", "urgent")
    val initialCreatedAt = first.createdAt
    val initialId = first.id

    syncService.upsertTimeEntry(
        userId = user.id,
        entry =
            TogglTimeEntry(
                id = 12345L,
                workspaceId = 7L,
                projectId = 100L,
                start = start,
                stop = stop.plusSeconds(60),
                description = "second pass",
                duration = 3660L,
                billable = false,
                tags = listOf("dev"),
            ),
    )

    timeEntryRepository.findAll() shouldHaveSize 1
    val reloaded = timeEntryRepository.findByTogglId(12345L)
    reloaded.shouldNotBeNull()
    reloaded.description shouldBe "second pass"
    reloaded.duration shouldBe 3660L
    reloaded.billable shouldBe false
    reloaded.tags shouldBe listOf("dev")
    reloaded.createdAt shouldBe initialCreatedAt
    reloaded.id shouldBe initialId
  }
}
