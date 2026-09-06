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

import io.github.mschout.gitlab.toggltimer.project.Project
import io.github.mschout.gitlab.toggltimer.project.ProjectRepository
import io.github.mschout.gitlab.toggltimer.project.TimeEntry
import io.github.mschout.gitlab.toggltimer.project.TimeEntryRepository
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimeEntryRestartServiceTest {

  private val timeEntryRepository = mockk<TimeEntryRepository>()
  private val projectRepository = mockk<ProjectRepository>()
  private val splitOperationRepository = mockk<TimeEntrySplitOperationRepository>()
  private val credentialsService = mockk<CurrentUserCredentialsService>()
  private val togglService = mockk<TogglService>()
  private lateinit var service: TimeEntryRestartService

  @BeforeEach
  fun setUp() {
    every { credentialsService.currentUserId() } returns 42L
    every { splitOperationRepository.findByUserIdAndOriginalTogglId(42L, any()) } returns null
    service =
        TimeEntryRestartService(
            timeEntryRepository,
            projectRepository,
            splitOperationRepository,
            credentialsService,
            togglService,
        )
  }

  @Test
  fun `restarts from the latest owned entry metadata`() {
    val entry = completedEntry(projectId = 99L, description = "Latest description")
    val project =
        Project(
            togglId = 99L,
            workspaceId = 7L,
            togglClientId = 5L,
            name = "Project",
            color = "#4C6EF5",
        )
    val expected = TimeEntryRestartOutcome.Started(mockk())
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns entry
    every { projectRepository.findByTogglIdAndWorkspaceIdAndActiveTrue(99L, 7L) } returns project
    every {
      togglService.restartTimer(
          project =
              match {
                it.id == 99L &&
                    it.workspaceId == 7L &&
                    it.name == "Project" &&
                    it.color == "#4C6EF5"
              },
          request =
              RestartTimerRequest(
                  workspaceId = 7L,
                  projectId = 99L,
                  description = "Latest description",
              ),
      )
    } returns expected

    service.restart(123L) shouldBe expected
  }

  @Test
  fun `preserves a projectless entry and normalizes a blank description`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        completedEntry(projectId = null, description = " ")
    val expected = TimeEntryRestartOutcome.Started(mockk())
    every {
      togglService.restartTimer(
          project = null,
          request = RestartTimerRequest(workspaceId = 7L, projectId = null, description = null),
      )
    } returns expected

    service.restart(123L) shouldBe expected

    verify(exactly = 0) { projectRepository.findByTogglIdAndWorkspaceIdAndActiveTrue(any(), any()) }
  }

  @Test
  fun `rejects an inaccessible or stale entry before calling Toggl`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns null

    service.restart(123L) shouldBe
        TimeEntryRestartOutcome.Rejected(
            "This time entry is no longer available. Refresh the page and try again."
        )

    verify(exactly = 0) { togglService.restartTimer(any(), any()) }
  }

  @Test
  fun `rejects an entry that is no longer completed before calling Toggl`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        completedEntry().also {
          it.stop = null
          it.duration = -1L
        }

    service.restart(123L) shouldBe
        TimeEntryRestartOutcome.Rejected(
            "This time entry is no longer available. Refresh the page and try again."
        )

    verify(exactly = 0) { togglService.restartTimer(any(), any()) }
  }

  @Test
  fun `rejects a server-deleted entry before calling Toggl`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        completedEntry().also { it.serverDeletedAt = Instant.parse("2026-09-05T14:00:00Z") }

    service.restart(123L) shouldBe
        TimeEntryRestartOutcome.Rejected(
            "This time entry is no longer available. Refresh the page and try again."
        )

    verify(exactly = 0) { togglService.restartTimer(any(), any()) }
  }

  @Test
  fun `rejects an inactive project before calling Toggl`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns
        completedEntry(projectId = 99L)
    every { projectRepository.findByTogglIdAndWorkspaceIdAndActiveTrue(99L, 7L) } returns null

    service.restart(123L) shouldBe
        TimeEntryRestartOutcome.Rejected(
            "The original project is no longer active in this workspace."
        )

    verify(exactly = 0) { togglService.restartTimer(any(), any()) }
  }

  @Test
  fun `rejects an entry with split reconciliation in progress before calling Toggl`() {
    every { timeEntryRepository.findByTogglIdAndUserId(123L, 42L) } returns completedEntry()
    every { splitOperationRepository.findByUserIdAndOriginalTogglId(42L, 123L) } returns mockk()

    service.restart(123L) shouldBe
        TimeEntryRestartOutcome.Rejected(
            "Finish reconciling this split before restarting the entry."
        )

    verify(exactly = 0) { togglService.restartTimer(any(), any()) }
  }

  private fun completedEntry(projectId: Long? = null, description: String? = "Work") =
      TimeEntry(
          togglId = 123L,
          userId = 42L,
          workspaceId = 7L,
          projectId = projectId,
          description = description,
          start = Instant.parse("2026-09-05T12:00:00Z"),
          stop = Instant.parse("2026-09-05T13:00:00Z"),
          duration = 3_600L,
      )
}
