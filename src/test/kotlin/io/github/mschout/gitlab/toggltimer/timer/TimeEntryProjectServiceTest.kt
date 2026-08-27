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

import io.github.mschout.gitlab.toggltimer.project.Client
import io.github.mschout.gitlab.toggltimer.project.ClientRepository
import io.github.mschout.gitlab.toggltimer.project.Project
import io.github.mschout.gitlab.toggltimer.project.ProjectRepository
import io.github.mschout.gitlab.toggltimer.project.TimeEntry
import io.github.mschout.gitlab.toggltimer.project.TimeEntryRepository
import io.github.mschout.gitlab.toggltimer.project.TogglSyncService
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.toggl.UpdateTimeEntryProjectRequest
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimeEntryProjectServiceTest {

  private val timeEntryRepository = mockk<TimeEntryRepository>()
  private val projectRepository = mockk<ProjectRepository>()
  private val clientRepository = mockk<ClientRepository>()
  private val togglClientFactory = mockk<TogglClientFactory>()
  private val credentialsService = mockk<CurrentUserCredentialsService>()
  private val togglSyncService = mockk<TogglSyncService>()
  private val togglClient = mockk<TogglClient>()
  private lateinit var service: TimeEntryProjectService

  @BeforeEach
  fun setUp() {
    every { credentialsService.currentUserId() } returns 42L
    every { credentialsService.requireTogglApiKey() } returns "api-key"
    every { togglClientFactory.forApiKey("api-key") } returns togglClient
    service =
        TimeEntryProjectService(
            timeEntryRepository,
            projectRepository,
            clientRepository,
            togglClientFactory,
            credentialsService,
            togglSyncService,
        )
  }

  @Test
  fun `lists active projects and clients for the stopped timer workspace`() {
    val projects =
        listOf(
            project(togglId = 100L, name = "100 - CourtDrive", clientId = 10L),
            project(togglId = 200L, name = "200 - Inforuptcy", clientId = null, color = "bad"),
        )
    every { projectRepository.findAllByWorkspaceIdAndActiveTrueOrderByNameAsc(7L) } returns projects
    every { clientRepository.findAllByTogglIdIn(listOf(10L)) } returns
        listOf(Client(togglId = 10L, workspaceId = 7L, name = "Courtio"))

    service.projectsForWorkspace(7L) shouldBe
        listOf(
            StoppedTimerProjectView(
                togglId = 100L,
                name = "100 - CourtDrive",
                clientName = "Courtio",
                color = "#4C6EF5",
            ),
            StoppedTimerProjectView(
                togglId = 200L,
                name = "200 - Inforuptcy",
                clientName = null,
                color = null,
            ),
        )
  }

  @Test
  fun `searches active projects by name within the time entry workspace`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry(projectId = 100L)
    val current = project(togglId = 100L, name = "100 - Indiana", clientId = 10L)
    val match =
        project(togglId = 200L, name = "200 - More Indiana", clientId = 20L, color = "#4C6EF5")
    every {
      projectRepository
          .findTop20ByWorkspaceIdAndActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(
              7L,
              "Indiana",
          )
    } returns listOf(current, match)
    every { clientRepository.findAllByTogglIdIn(listOf(10L, 20L)) } returns
        listOf(
            Client(togglId = 10L, workspaceId = 7L, name = "CourtDrive"),
            Client(togglId = 20L, workspaceId = 7L, name = "Inforuptcy"),
        )

    val result = service.searchProjects(togglId = 123L, query = " Indiana ")

    assertSoftly(result) {
      togglId shouldBe 123L
      query shouldBe " Indiana "
      projects shouldHaveSize 2
      projects[0].selected shouldBe true
      projects[0].clientName shouldBe "CourtDrive"
      projects[1].selected shouldBe false
      projects[1].clientName shouldBe "Inforuptcy"
      projects[1].color shouldBe "#4C6EF5"
    }
  }

  @Test
  fun `blank search returns the first active projects in the workspace`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry()
    every { projectRepository.findTop20ByWorkspaceIdAndActiveTrueOrderByNameAsc(7L) } returns
        emptyList()

    val result = service.searchProjects(togglId = 123L, query = "  ")

    result.projects shouldBe emptyList()
    verify(exactly = 0) { clientRepository.findAllByTogglIdIn(any()) }
  }

  @Test
  fun `updates Toggl with workspace and project then persists the returned entry`() {
    val selected = project(togglId = 200L, name = "200 - Indiana", clientId = 20L)
    val updated = togglEntry(projectId = 200L)
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry(projectId = 100L)
    every { projectRepository.findByTogglIdAndWorkspaceIdAndActiveTrue(200L, 7L) } returns selected
    every {
      togglClient.updateTimeEntryProject(
          7L,
          123L,
          UpdateTimeEntryProjectRequest(workspaceId = 7L, projectId = 200L),
      )
    } returns updated
    every { togglSyncService.upsertTimeEntry(42L, updated) } returns entry(projectId = 200L)
    every { clientRepository.findByTogglId(20L) } returns
        Client(togglId = 20L, workspaceId = 7L, name = "Inforuptcy")

    val result = service.updateProject(togglId = 123L, projectId = 200L)

    result shouldBe
        TimeEntryProjectPickerView(
            togglId = 123L,
            projectName = "200 - Indiana",
            clientName = "Inforuptcy",
            projectColor = "#4C6EF5",
        )
    verify(exactly = 1) { togglSyncService.upsertTimeEntry(42L, updated) }
  }

  @Test
  fun `rejects a project outside the time entry workspace`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry()
    every { projectRepository.findByTogglIdAndWorkspaceIdAndActiveTrue(999L, 7L) } returns null

    shouldThrow<TimeEntryProjectNotFoundException> {
      service.updateProject(togglId = 123L, projectId = 999L)
    }

    verify(exactly = 0) { credentialsService.requireTogglApiKey() }
    verify(exactly = 0) { togglSyncService.upsertTimeEntry(any(), any()) }
  }

  @Test
  fun `entry owned by another user cannot search or update projects`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns null

    shouldThrow<TimeEntryProjectNotFoundException> {
      service.searchProjects(togglId = 123L, query = "Indiana")
    }
    shouldThrow<TimeEntryProjectNotFoundException> {
      service.updateProject(togglId = 123L, projectId = 200L)
    }

    verify(exactly = 0) { projectRepository.findAll() }
    verify(exactly = 0) { credentialsService.requireTogglApiKey() }
  }

  @Test
  fun `unchanged project skips Toggl and Postgres`() {
    val current = project(togglId = 100L, name = "Current", clientId = null)
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry(projectId = 100L)
    every { projectRepository.findByTogglIdAndWorkspaceIdAndActiveTrue(100L, 7L) } returns current

    val result = service.updateProject(togglId = 123L, projectId = 100L)

    result.projectName shouldBe "Current"
    verify(exactly = 0) { credentialsService.requireTogglApiKey() }
    verify(exactly = 0) { togglSyncService.upsertTimeEntry(any(), any()) }
  }

  @Test
  fun `Toggl failure does not update Postgres`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry()
    every { projectRepository.findByTogglIdAndWorkspaceIdAndActiveTrue(200L, 7L) } returns
        project(togglId = 200L, name = "Selected")
    every { togglClient.updateTimeEntryProject(any(), any(), any()) } throws
        RuntimeException("Toggl down")

    shouldThrow<TogglProjectUpdateException> {
      service.updateProject(togglId = 123L, projectId = 200L)
    }

    verify(exactly = 0) { togglSyncService.upsertTimeEntry(any(), any()) }
  }

  @Test
  fun `Postgres failure is reported after Toggl succeeds`() {
    val updated = togglEntry(projectId = 200L)
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry()
    every { projectRepository.findByTogglIdAndWorkspaceIdAndActiveTrue(200L, 7L) } returns
        project(togglId = 200L, name = "Selected")
    every { togglClient.updateTimeEntryProject(any(), any(), any()) } returns updated
    every { togglSyncService.upsertTimeEntry(42L, updated) } throws
        RuntimeException("Postgres down")

    shouldThrow<TimeEntryProjectHistoryUpdateException> {
      service.updateProject(togglId = 123L, projectId = 200L)
    }

    verify(exactly = 1) { togglClient.updateTimeEntryProject(any(), any(), any()) }
  }

  private fun entry(projectId: Long? = null) =
      TimeEntry(
          togglId = 123L,
          userId = 42L,
          workspaceId = 7L,
          projectId = projectId,
          start = Instant.parse("2026-08-26T12:00:00Z"),
          stop = Instant.parse("2026-08-26T13:00:00Z"),
          duration = 3_600L,
      )

  private fun project(
      togglId: Long,
      name: String,
      clientId: Long? = null,
      color: String? = "#4C6EF5",
  ) =
      Project(
          togglId = togglId,
          workspaceId = 7L,
          togglClientId = clientId,
          name = name,
          color = color,
      )

  private fun togglEntry(projectId: Long) =
      TogglTimeEntry(
          id = 123L,
          workspaceId = 7L,
          projectId = projectId,
          start = Instant.parse("2026-08-26T12:00:00Z"),
          stop = Instant.parse("2026-08-26T13:00:00Z"),
          duration = 3_600L,
      )
}
