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

import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspaceClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TogglSyncServiceTest {

  private lateinit var clientRepository: ClientRepository
  private lateinit var projectRepository: ProjectRepository
  private lateinit var service: TogglSyncService

  @BeforeEach
  fun setUp() {
    clientRepository = mockk(relaxed = true)
    projectRepository = mockk(relaxed = true)
    service = TogglSyncService(clientRepository, projectRepository)
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
}
