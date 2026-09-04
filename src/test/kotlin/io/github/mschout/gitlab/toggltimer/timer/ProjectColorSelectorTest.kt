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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProjectColorSelectorTest {

  private val timeEntryRepository = mockk<TimeEntryRepository>()
  private val projectRepository = mockk<ProjectRepository>()
  private val credentialsService = mockk<CurrentUserCredentialsService>()
  private lateinit var selector: ProjectColorSelector

  @BeforeEach
  fun setUp() {
    every { credentialsService.currentUserId() } returns 42L
    every { timeEntryRepository.findLatestCompletedForColorSelection(42L, any()) } returns
        emptyList()
    every { timeEntryRepository.findLatestRunningForColorSelection(42L, any()) } returns emptyList()
    selector = ProjectColorSelector(timeEntryRepository, projectRepository, credentialsService)
  }

  @Test
  fun `excludes completed and running project colors without regard to case`() {
    every { timeEntryRepository.findLatestCompletedForColorSelection(42L, any()) } returns
        listOf(entry(togglId = 1L, projectId = 100L))
    every { timeEntryRepository.findLatestRunningForColorSelection(42L, any()) } returns
        listOf(entry(togglId = 2L, projectId = 200L))
    every { projectRepository.findAllByTogglIdIn(listOf(100L, 200L)) } returns
        listOf(
            project(togglId = 100L, color = "#EF4444"),
            project(togglId = 200L, color = "#3b82f6"),
        )

    repeat(50) {
      val selected = selector.select()

      ProjectColorSelector.PROJECT_COLOR_PALETTE shouldContain selected
      selected shouldNotBe "#ef4444"
      selected shouldNotBe "#3b82f6"
    }
  }

  @Test
  fun `deduplicates the same color from both reference entries`() {
    every { timeEntryRepository.findLatestCompletedForColorSelection(42L, any()) } returns
        listOf(entry(togglId = 1L, projectId = 100L))
    every { timeEntryRepository.findLatestRunningForColorSelection(42L, any()) } returns
        listOf(entry(togglId = 2L, projectId = 200L))
    every { projectRepository.findAllByTogglIdIn(listOf(100L, 200L)) } returns
        listOf(
            project(togglId = 100L, color = "#ef4444"),
            project(togglId = 200L, color = "#EF4444"),
        )

    selector.select() shouldNotBe "#ef4444"
  }

  @Test
  fun `does not scan past a projectless newest entry`() {
    every { timeEntryRepository.findLatestCompletedForColorSelection(42L, any()) } returns
        listOf(entry(togglId = 1L, projectId = null))

    ProjectColorSelector.PROJECT_COLOR_PALETTE shouldContain selector.select()
    verify(exactly = 0) { projectRepository.findAllByTogglIdIn(any()) }
  }

  @Test
  fun `ignores colorless and out of palette projects`() {
    every { timeEntryRepository.findLatestCompletedForColorSelection(42L, any()) } returns
        listOf(entry(togglId = 1L, projectId = 100L))
    every { timeEntryRepository.findLatestRunningForColorSelection(42L, any()) } returns
        listOf(entry(togglId = 2L, projectId = 200L))
    every { projectRepository.findAllByTogglIdIn(listOf(100L, 200L)) } returns
        listOf(project(togglId = 100L, color = null), project(togglId = 200L, color = "#123456"))

    ProjectColorSelector.PROJECT_COLOR_PALETTE shouldContain selector.select()
  }

  @Test
  fun `ignores a missing reference project`() {
    every { timeEntryRepository.findLatestCompletedForColorSelection(42L, any()) } returns
        listOf(entry(togglId = 1L, projectId = 100L))
    every { projectRepository.findAllByTogglIdIn(listOf(100L)) } returns emptyList()

    ProjectColorSelector.PROJECT_COLOR_PALETTE shouldContain selector.select()
  }

  @Test
  fun `uses a running exclusion when the completed lookup fails`() {
    every { timeEntryRepository.findLatestCompletedForColorSelection(42L, any()) } throws
        IllegalStateException("completed lookup failed")
    every { timeEntryRepository.findLatestRunningForColorSelection(42L, any()) } returns
        listOf(entry(togglId = 2L, projectId = 200L))
    every { projectRepository.findAllByTogglIdIn(listOf(200L)) } returns
        listOf(project(togglId = 200L, color = "#ef4444"))

    selector.select() shouldNotBe "#ef4444"
  }

  @Test
  fun `falls back to the full palette when project lookup fails`() {
    every { timeEntryRepository.findLatestCompletedForColorSelection(42L, any()) } returns
        listOf(entry(togglId = 1L, projectId = 100L))
    every { projectRepository.findAllByTogglIdIn(listOf(100L)) } throws
        IllegalStateException("project lookup failed")

    ProjectColorSelector.PROJECT_COLOR_PALETTE shouldContain selector.select()
  }

  @Test
  fun `falls back to the full palette when the current user cannot be resolved`() {
    every { credentialsService.currentUserId() } throws IllegalStateException("user lookup failed")

    ProjectColorSelector.PROJECT_COLOR_PALETTE shouldContain selector.select()
    verify(exactly = 0) {
      timeEntryRepository.findLatestCompletedForColorSelection(any(), any())
      timeEntryRepository.findLatestRunningForColorSelection(any(), any())
    }
  }

  private fun entry(togglId: Long, projectId: Long?): TimeEntry =
      TimeEntry(
          togglId = togglId,
          userId = 42L,
          workspaceId = 7L,
          projectId = projectId,
          start = Instant.parse("2026-09-04T12:00:00Z"),
          duration = 60L,
      )

  private fun project(togglId: Long, color: String?): Project =
      Project(togglId = togglId, workspaceId = 7L, name = "Project $togglId", color = color)
}
