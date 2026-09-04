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

import io.github.mschout.gitlab.toggltimer.toggl.TogglClientProperties
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspaceClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TogglSyncServiceTest {

  private lateinit var workspaceRepository: WorkspaceRepository
  private lateinit var clientRepository: ClientRepository
  private lateinit var projectRepository: ProjectRepository
  private lateinit var timeEntryRepository: TimeEntryRepository
  private lateinit var service: TogglSyncService

  @BeforeEach
  fun setUp() {
    workspaceRepository = mockk(relaxed = true)
    clientRepository = mockk(relaxed = true)
    projectRepository = mockk(relaxed = true)
    timeEntryRepository = mockk(relaxed = true)
    service =
        TogglSyncService(
            workspaceRepository,
            clientRepository,
            projectRepository,
            timeEntryRepository,
            TogglClientProperties(
                baseUrl = "https://api.track.toggl.com/api/v9",
                defaultProjectColor = "#abcdef",
            ),
        )
  }

  @Test
  fun `upsertWorkspaces is a no-op when the list is empty`() {
    service.upsertWorkspaces(emptyList())

    verify(exactly = 0) { workspaceRepository.findAllByTogglIdIn(any()) }
    verify(exactly = 0) { workspaceRepository.save(any()) }
  }

  @Test
  fun `upsertWorkspaces inserts new workspaces`() {
    every { workspaceRepository.findAllByTogglIdIn(listOf(1L, 2L)) } returns emptyList()
    val saved = mutableListOf<Workspace>()
    every { workspaceRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertWorkspaces(
        listOf(TogglWorkspace(id = 1L, name = "Alpha"), TogglWorkspace(id = 2L, name = "Beta"))
    )

    saved.size shouldBe 2
    saved[0].togglId shouldBe 1L
    saved[0].name shouldBe "Alpha"
    saved[1].togglId shouldBe 2L
    saved[1].name shouldBe "Beta"
  }

  @Test
  fun `upsertWorkspaces updates existing workspaces in place`() {
    val existing = Workspace(togglId = 1L, name = "Old Alpha")
    every { workspaceRepository.findAllByTogglIdIn(listOf(1L)) } returns listOf(existing)
    val saved = slot<Workspace>()
    every { workspaceRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertWorkspaces(listOf(TogglWorkspace(id = 1L, name = "New Alpha")))

    saved.captured shouldBeSameInstanceAs existing
    existing.name shouldBe "New Alpha"
  }

  @Test
  fun `upsertWorkspaces handles a mix of new and existing workspaces`() {
    val existing = Workspace(togglId = 1L, name = "Old Alpha")
    every { workspaceRepository.findAllByTogglIdIn(listOf(1L, 2L)) } returns listOf(existing)
    val saved = mutableListOf<Workspace>()
    every { workspaceRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertWorkspaces(
        listOf(TogglWorkspace(id = 1L, name = "New Alpha"), TogglWorkspace(id = 2L, name = "Beta"))
    )

    saved.size shouldBe 2
    saved[0] shouldBeSameInstanceAs existing
    saved[0].name shouldBe "New Alpha"
    saved[1].togglId shouldBe 2L
    saved[1].name shouldBe "Beta"
  }

  @Test
  fun `upsertClients is a no-op when the list is empty`() {
    service.upsertClients(7L, emptyList())

    verify(exactly = 0) { clientRepository.findAllByTogglIdIn(any()) }
    verify(exactly = 0) { clientRepository.save(any()) }
  }

  @Test
  fun `upsertClients inserts new clients`() {
    every { clientRepository.findAllByTogglIdIn(listOf(10L, 11L)) } returns emptyList()
    val saved = mutableListOf<Client>()
    every { clientRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertClients(
        7L,
        listOf(
            TogglWorkspaceClient(id = 10L, name = "Globex"),
            TogglWorkspaceClient(id = 11L, name = "Initech"),
        ),
    )

    saved.size shouldBe 2
    saved[0].togglId shouldBe 10L
    saved[0].workspaceId shouldBe 7L
    saved[0].name shouldBe "Globex"
    saved[1].togglId shouldBe 11L
    saved[1].name shouldBe "Initech"
  }

  @Test
  fun `upsertClients updates existing clients in place`() {
    val existing = Client(togglId = 10L, workspaceId = 99L, name = "Old Globex")
    every { clientRepository.findAllByTogglIdIn(listOf(10L)) } returns listOf(existing)
    val saved = slot<Client>()
    every { clientRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertClients(7L, listOf(TogglWorkspaceClient(id = 10L, name = "New Globex")))

    saved.captured shouldBeSameInstanceAs existing
    existing.workspaceId shouldBe 7L
    existing.name shouldBe "New Globex"
  }

  @Test
  fun `upsertClients handles a mix of new and existing clients`() {
    val existing = Client(togglId = 10L, workspaceId = 7L, name = "Old Globex")
    every { clientRepository.findAllByTogglIdIn(listOf(10L, 11L)) } returns listOf(existing)
    val saved = mutableListOf<Client>()
    every { clientRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertClients(
        7L,
        listOf(
            TogglWorkspaceClient(id = 10L, name = "New Globex"),
            TogglWorkspaceClient(id = 11L, name = "Initech"),
        ),
    )

    saved.size shouldBe 2
    saved[0] shouldBeSameInstanceAs existing
    saved[0].name shouldBe "New Globex"
    saved[1].togglId shouldBe 11L
    saved[1].name shouldBe "Initech"
  }

  @Test
  fun `upsertProject inserts a new project with all fields populated`() {
    every { projectRepository.findByTogglId(999L) } returns null
    val saved = slot<Project>()
    every { projectRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertProject(
        7L,
        TogglProject(
            id = 999L,
            name = "42 - New",
            clientId = 5L,
            workspaceId = 7L,
            color = "#ef4444",
            active = true,
        ),
    )

    saved.captured.togglId shouldBe 999L
    saved.captured.workspaceId shouldBe 7L
    saved.captured.togglClientId shouldBe 5L
    saved.captured.name shouldBe "42 - New"
    saved.captured.color shouldBe "#ef4444"
    saved.captured.active shouldBe true
  }

  @Test
  fun `upsertProject defaults active to true when DTO active is null`() {
    every { projectRepository.findByTogglId(999L) } returns null
    val saved = slot<Project>()
    every { projectRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertProject(7L, TogglProject(id = 999L, name = "42 - New", active = null))

    saved.captured.active shouldBe true
  }

  @Test
  fun `upsertProject allows null clientId and color`() {
    every { projectRepository.findByTogglId(999L) } returns null
    val saved = slot<Project>()
    every { projectRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertProject(
        7L,
        TogglProject(id = 999L, name = "42 - No client", clientId = null, color = null),
    )

    saved.captured.togglClientId shouldBe null
    saved.captured.color shouldBe null
  }

  @Test
  fun `upsertProject updates an existing project in place`() {
    val existing =
        Project(
            togglId = 999L,
            workspaceId = 99L,
            togglClientId = 1L,
            name = "old name",
            color = "#000000",
            active = false,
        )
    every { projectRepository.findByTogglId(999L) } returns existing
    val saved = slot<Project>()
    every { projectRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertProject(
        7L,
        TogglProject(id = 999L, name = "new name", clientId = 5L, color = "#ef4444", active = true),
    )

    saved.captured shouldBeSameInstanceAs existing
    existing.workspaceId shouldBe 7L
    existing.togglClientId shouldBe 5L
    existing.name shouldBe "new name"
    existing.color shouldBe "#ef4444"
    existing.active shouldBe true
  }

  @Test
  fun `upsertProject preserves an existing color when Toggl reports the configured default color`() {
    val existing = Project(togglId = 999L, workspaceId = 7L, name = "existing", color = "#ef4444")
    every { projectRepository.findByTogglId(999L) } returns existing
    every { projectRepository.save(any()) } answers { firstArg() }

    service.upsertProject(7L, TogglProject(id = 999L, name = "renamed", color = "#abcdef"))

    existing.name shouldBe "renamed"
    existing.color shouldBe "#ef4444"
  }

  @Test
  fun `upsertProject stores the configured default color when no local color exists`() {
    val existing = Project(togglId = 999L, workspaceId = 7L, name = "existing", color = null)
    every { projectRepository.findByTogglId(999L) } returns existing
    every { projectRepository.save(any()) } answers { firstArg() }

    service.upsertProject(7L, TogglProject(id = 999L, name = "renamed", color = "#abcdef"))

    existing.color shouldBe "#abcdef"
  }

  @Test
  fun `upsertProject throws when Toggl id is missing`() {
    shouldThrow<IllegalArgumentException> {
      service.upsertProject(7L, TogglProject(id = null, name = "x"))
    }
  }

  @Test
  fun `upsertProject throws when Toggl name is missing`() {
    shouldThrow<IllegalArgumentException> {
      service.upsertProject(7L, TogglProject(id = 999L, name = null))
    }
  }

  @Test
  fun `upsertProjects is a no-op when the list is empty`() {
    service.upsertProjects(7L, emptyList())

    verify(exactly = 0) { projectRepository.findByTogglId(any()) }
    verify(exactly = 0) { projectRepository.save(any()) }
  }

  @Test
  fun `upsertProjects upserts each project in the batch`() {
    every { projectRepository.findByTogglId(any()) } returns null
    val saved = mutableListOf<Project>()
    every { projectRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertProjects(
        7L,
        listOf(
            TogglProject(id = 1L, name = "Alpha", clientId = 5L),
            TogglProject(id = 2L, name = "Beta", clientId = null),
        ),
    )

    saved.size shouldBe 2
    saved[0].togglId shouldBe 1L
    saved[0].workspaceId shouldBe 7L
    saved[0].name shouldBe "Alpha"
    saved[0].togglClientId shouldBe 5L
    saved[1].togglId shouldBe 2L
    saved[1].name shouldBe "Beta"
    saved[1].togglClientId shouldBe null
  }

  @Test
  fun `upsertTimeEntries is a no-op when the list is empty`() {
    service.upsertTimeEntries(42L, emptyList())

    verify(exactly = 0) { timeEntryRepository.findByTogglId(any()) }
    verify(exactly = 0) { timeEntryRepository.save(any()) }
  }

  @Test
  fun `upsertTimeEntry inserts a new entry with all fields populated`() {
    val start = Instant.parse("2026-05-22T12:00:00Z")
    val stop = Instant.parse("2026-05-22T13:00:00Z")
    val at = Instant.parse("2026-05-22T13:00:01Z")
    every { timeEntryRepository.findByTogglId(999L) } returns null
    val saved = slot<TimeEntry>()
    every { timeEntryRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertTimeEntry(
        userId = 42L,
        entry =
            TogglTimeEntry(
                id = 999L,
                workspaceId = 7L,
                projectId = 100L,
                taskId = 200L,
                userId = 300L,
                start = start,
                stop = stop,
                description = "hacking",
                duration = 3600L,
                billable = true,
                tags = listOf("dev", "urgent"),
                createdWith = "Gitlab Toggl Timer",
                at = at,
            ),
    )

    saved.captured.togglId shouldBe 999L
    saved.captured.userId shouldBe 42L
    saved.captured.togglUserId shouldBe 300L
    saved.captured.workspaceId shouldBe 7L
    saved.captured.projectId shouldBe 100L
    saved.captured.taskId shouldBe 200L
    saved.captured.description shouldBe "hacking"
    saved.captured.start shouldBe start
    saved.captured.stop shouldBe stop
    saved.captured.duration shouldBe 3600L
    saved.captured.billable shouldBe true
    saved.captured.tags shouldBe listOf("dev", "urgent")
    saved.captured.createdWith shouldBe "Gitlab Toggl Timer"
    saved.captured.togglAt shouldBe at
    saved.captured.serverDeletedAt shouldBe null
  }

  @Test
  fun `upsertTimeEntry defaults billable to false and tags to empty when DTO has nulls`() {
    every { timeEntryRepository.findByTogglId(999L) } returns null
    val saved = slot<TimeEntry>()
    every { timeEntryRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertTimeEntry(
        userId = 42L,
        entry =
            TogglTimeEntry(
                id = 999L,
                workspaceId = 7L,
                start = Instant.parse("2026-05-22T12:00:00Z"),
                duration = -1L,
                billable = null,
                tags = null,
            ),
    )

    saved.captured.billable shouldBe false
    saved.captured.tags shouldBe emptyList()
  }

  @Test
  fun `upsertTimeEntry updates an existing entry in place`() {
    val existing =
        TimeEntry(
            togglId = 999L,
            userId = 42L,
            workspaceId = 7L,
            start = Instant.parse("2026-05-22T12:00:00Z"),
            duration = -1L,
            description = "old",
            tags = listOf("old-tag"),
        )
    every { timeEntryRepository.findByTogglId(999L) } returns existing
    val saved = slot<TimeEntry>()
    every { timeEntryRepository.save(capture(saved)) } answers { firstArg() }

    val newStop = Instant.parse("2026-05-22T13:00:00Z")
    val deletedAt = Instant.parse("2026-05-22T13:05:00Z")
    service.upsertTimeEntry(
        userId = 42L,
        entry =
            TogglTimeEntry(
                id = 999L,
                workspaceId = 7L,
                start = Instant.parse("2026-05-22T12:00:00Z"),
                stop = newStop,
                description = "new",
                duration = 3600L,
                billable = true,
                tags = listOf("new-tag"),
                serverDeletedAt = deletedAt,
            ),
    )

    saved.captured shouldBeSameInstanceAs existing
    existing.stop shouldBe newStop
    existing.description shouldBe "new"
    existing.duration shouldBe 3600L
    existing.billable shouldBe true
    existing.tags shouldBe listOf("new-tag")
    existing.serverDeletedAt shouldBe deletedAt
  }

  @Test
  fun `upsertTimeEntries upserts each entry in the batch`() {
    every { timeEntryRepository.findByTogglId(any()) } returns null
    val saved = mutableListOf<TimeEntry>()
    every { timeEntryRepository.save(capture(saved)) } answers { firstArg() }

    service.upsertTimeEntries(
        userId = 42L,
        entries =
            listOf(
                TogglTimeEntry(
                    id = 1L,
                    workspaceId = 7L,
                    start = Instant.parse("2026-05-22T12:00:00Z"),
                    duration = 100L,
                ),
                TogglTimeEntry(
                    id = 2L,
                    workspaceId = 7L,
                    start = Instant.parse("2026-05-22T13:00:00Z"),
                    duration = 200L,
                ),
            ),
    )

    saved.size shouldBe 2
    saved[0].togglId shouldBe 1L
    saved[1].togglId shouldBe 2L
  }

  @Test
  fun `upsertTimeEntries removes tombstones without recreating missing entries`() {
    val existing =
        TimeEntry(
            togglId = 1L,
            userId = 42L,
            workspaceId = 7L,
            start = Instant.parse("2026-05-22T12:00:00Z"),
            duration = 100L,
        )
    every { timeEntryRepository.findByTogglId(1L) } returns existing
    every { timeEntryRepository.findByTogglId(2L) } returns null

    service.upsertTimeEntries(
        userId = 42L,
        entries =
            listOf(
                TogglTimeEntry(
                    id = 1L,
                    workspaceId = 7L,
                    start = Instant.parse("2026-05-22T12:00:00Z"),
                    duration = 100L,
                    serverDeletedAt = Instant.parse("2026-05-22T13:00:00Z"),
                ),
                TogglTimeEntry(
                    id = 2L,
                    workspaceId = 7L,
                    start = Instant.parse("2026-05-22T14:00:00Z"),
                    duration = 100L,
                    serverDeletedAt = Instant.parse("2026-05-22T15:00:00Z"),
                ),
            ),
    )

    verify(exactly = 1) { timeEntryRepository.delete(existing) }
    verify(exactly = 0) { timeEntryRepository.save(any()) }
    verify(exactly = 0) { projectRepository.save(any()) }
    verify(exactly = 0) { clientRepository.save(any()) }
  }

  @Test
  fun `upsertTimeEntry throws when Toggl id is missing`() {
    shouldThrow<IllegalArgumentException> {
      service.upsertTimeEntry(
          userId = 42L,
          entry = TogglTimeEntry(id = null, workspaceId = 7L, start = Instant.now()),
      )
    }
  }

  @Test
  fun `upsertTimeEntry throws when start is missing`() {
    shouldThrow<IllegalArgumentException> {
      service.upsertTimeEntry(
          userId = 42L,
          entry = TogglTimeEntry(id = 999L, workspaceId = 7L, start = null),
      )
    }
  }

  @Test
  fun `upsertTimeEntry throws when workspaceId is missing`() {
    shouldThrow<IllegalArgumentException> {
      service.upsertTimeEntry(
          userId = 42L,
          entry = TogglTimeEntry(id = 999L, workspaceId = null, start = Instant.now()),
      )
    }
  }
}
