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

@SpringBootTest(properties = ["toggl.default-project-color=#abcdef"])
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

    syncService.upsertProject(
        7L,
        TogglProject(
            id = 999L,
            name = "42 - Toggl default color",
            clientId = 11L,
            color = "#abcdef",
            active = true,
        ),
    )

    val afterDefaultColorSync = projectRepository.findByTogglId(999L)
    afterDefaultColorSync.shouldNotBeNull()
    afterDefaultColorSync.name shouldBe "42 - Toggl default color"
    afterDefaultColorSync.color shouldBe "#10b981"
    afterDefaultColorSync.active shouldBe true
  }

  @Test
  fun `project search is case insensitive and limited to active projects in one workspace`() {
    projectRepository.saveAll(
        listOf(
            Project(togglId = 101L, workspaceId = 7L, name = "Alpha Indiana"),
            Project(togglId = 102L, workspaceId = 7L, name = "Zulu INDIANA"),
            Project(togglId = 103L, workspaceId = 7L, name = "Inactive Indiana", active = false),
            Project(togglId = 104L, workspaceId = 8L, name = "Other Indiana"),
            Project(togglId = 105L, workspaceId = 7L, name = "Beta Colorado"),
        )
    )

    val matches =
        projectRepository
            .findTop20ByWorkspaceIdAndActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(
                workspaceId = 7L,
                name = "indiana",
            )
    val initial = projectRepository.findTop20ByWorkspaceIdAndActiveTrueOrderByNameAsc(7L)

    matches.map { it.togglId } shouldBe listOf(101L, 102L)
    initial.map { it.togglId } shouldBe listOf(101L, 105L, 102L)
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

  @Test
  fun `completed history query isolates users and excludes running deleted and out of range rows`() {
    val user = userRepository.save(User(email = "history-${System.nanoTime()}@example.com"))
    val otherUser = userRepository.save(User(email = "other-${System.nanoTime()}@example.com"))
    val rangeStart = Instant.parse("2026-08-20T05:00:00Z")
    val rangeEnd = Instant.parse("2026-08-27T05:00:00Z")

    timeEntryRepository.saveAll(
        listOf(
            historyEntry(1L, user.id, "2026-08-25T16:00:00Z"),
            historyEntry(2L, user.id, "2026-08-26T16:00:00Z"),
            historyEntry(3L, otherUser.id, "2026-08-26T17:00:00Z"),
            historyEntry(4L, user.id, "2026-08-26T18:00:00Z", running = true),
            historyEntry(5L, user.id, "2026-08-26T19:00:00Z", deleted = true),
            historyEntry(6L, user.id, "2026-08-19T16:00:00Z"),
        )
    )

    val entries =
        timeEntryRepository.findCompletedInRange(
            userId = user.id,
            startInclusive = rangeStart,
            endExclusive = rangeEnd,
        )

    entries.map { it.togglId } shouldBe listOf(2L, 1L)
    timeEntryRepository.existsCompletedBefore(user.id, rangeStart) shouldBe true
    timeEntryRepository.existsCompletedBefore(otherUser.id, rangeStart) shouldBe false
  }

  private fun historyEntry(
      togglId: Long,
      userId: Long,
      start: String,
      running: Boolean = false,
      deleted: Boolean = false,
  ): TimeEntry {
    val startInstant = Instant.parse(start)
    return TimeEntry(
        togglId = togglId,
        userId = userId,
        workspaceId = 7L,
        start = startInstant,
        stop = if (running) null else startInstant.plusSeconds(60),
        duration = if (running) -1L else 60L,
        serverDeletedAt = if (deleted) startInstant.plusSeconds(120) else null,
    )
  }
}
