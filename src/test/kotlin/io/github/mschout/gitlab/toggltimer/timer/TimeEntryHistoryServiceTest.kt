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
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimeEntryHistoryServiceTest {

  private val timeEntryRepository = mockk<TimeEntryRepository>()
  private val projectRepository = mockk<ProjectRepository>()
  private val clientRepository = mockk<ClientRepository>()
  private val credentialsService = mockk<CurrentUserCredentialsService>()
  private val clock = Clock.fixed(Instant.parse("2026-08-26T17:00:00Z"), ZoneOffset.UTC)
  private lateinit var service: TimeEntryHistoryService

  @BeforeEach
  fun setUp() {
    every { credentialsService.currentUserId() } returns 42L
    every { credentialsService.currentTimeZone() } returns ZoneId.of("America/Chicago")
    service =
        TimeEntryHistoryService(
            timeEntryRepository,
            projectRepository,
            clientRepository,
            credentialsService,
            clock,
        )
  }

  @Test
  fun `initial page groups entries by local day and resolves project and client metadata`() {
    val todayEntry =
        entry(
            togglId = 1L,
            start = "2026-08-26T17:36:00Z",
            stop = "2026-08-26T18:24:02Z",
            duration = 2_882L,
            description = "Review merge request",
            projectId = 74393L,
        )
    val secondTodayEntry =
        entry(
            togglId = 2L,
            start = "2026-08-26T15:00:00Z",
            stop = "2026-08-26T15:14:58Z",
            duration = 898L,
            description = "Preserve document numbers",
            projectId = 74393L,
        )
    val yesterdayEntry =
        entry(
            togglId = 3L,
            start = "2026-08-25T23:30:00Z",
            stop = "2026-08-26T00:00:00Z",
            duration = 1_800L,
            description = null,
        )
    every {
      timeEntryRepository.findCompletedInRange(
          userId = 42L,
          startInclusive = Instant.parse("2026-08-20T05:00:00Z"),
          endExclusive = Instant.parse("2026-08-27T05:00:00Z"),
      )
    } returns listOf(todayEntry, secondTodayEntry, yesterdayEntry)
    val project =
        Project(
            togglId = 74393L,
            workspaceId = 7L,
            togglClientId = 9L,
            name = "74393 - Handle multi-doc emails in Indiana",
            color = "#4C6EF5",
        )
    every { projectRepository.findAllByTogglIdIn(listOf(74393L)) } returns listOf(project)
    every { clientRepository.findAllByTogglIdIn(listOf(9L)) } returns
        listOf(Client(togglId = 9L, workspaceId = 7L, name = "Inforuptcy"))
    every {
      timeEntryRepository.existsCompletedBefore(
          userId = 42L,
          before = Instant.parse("2026-08-20T05:00:00Z"),
      )
    } returns true

    val page = service.initialPage()

    assertSoftly(page) {
      initial shouldBe true
      rangeLabel shouldBe "Aug 20–Aug 26, 2026"
      nextBefore shouldBe LocalDate.parse("2026-08-20")
      groups shouldHaveSize 2
      groups[0].label shouldBe "Today"
      groups[0].totalFormatted shouldBe "1:03:00"
      groups[0].entries[0] shouldBe
          RecentTimeEntryView(
              descriptionEditor =
                  TimeEntryDescriptionEditorView(
                      togglId = 1L,
                      description = "Review merge request",
                  ),
              projectPicker =
                  TimeEntryProjectPickerView(
                      togglId = 1L,
                      projectName = "74393 - Handle multi-doc emails in Indiana",
                      clientName = "Inforuptcy",
                      projectColor = "#4C6EF5",
                  ),
              actions =
                  TimeEntryActionsView(
                      togglId = 1L,
                      description = "Review merge request",
                      split =
                          TimeEntrySplitView(
                              togglId = 1L,
                              expectedStart = Instant.parse("2026-08-26T17:36:00Z"),
                              expectedStop = Instant.parse("2026-08-26T18:24:02Z"),
                              durationSeconds = 2_882L,
                              splitOffsetSeconds = 1_441L,
                              timeZone = "America/Chicago",
                              startEpochMilliseconds =
                                  Instant.parse("2026-08-26T17:36:00Z").toEpochMilli(),
                              startLocalSecondOfDay = 45_360,
                              startOffsetSeconds = -18_000,
                              stopOffsetSeconds = -18_000,
                          ),
                  ),
              timeRange = "12:36 PM – 1:24 PM",
              durationFormatted = "0:48:02",
          )
      groups[1].label shouldBe "Yesterday"
      groups[1].entries.single().descriptionEditor.description.shouldBeNull()
    }
  }

  @Test
  fun `older page uses the preceding seven local dates and omits load more at the end`() {
    every {
      timeEntryRepository.findCompletedInRange(
          userId = 42L,
          startInclusive = Instant.parse("2026-08-13T05:00:00Z"),
          endExclusive = Instant.parse("2026-08-20T05:00:00Z"),
      )
    } returns emptyList()
    every {
      timeEntryRepository.existsCompletedBefore(
          userId = 42L,
          before = Instant.parse("2026-08-13T05:00:00Z"),
      )
    } returns false

    val page = service.pageBefore(LocalDate.parse("2026-08-20"))

    assertSoftly(page) {
      initial shouldBe false
      groups shouldBe emptyList()
      rangeLabel shouldBe "Aug 13–Aug 19, 2026"
      nextBefore.shouldBeNull()
    }
    verify(exactly = 0) { projectRepository.findAllByTogglIdIn(any()) }
    verify(exactly = 0) { clientRepository.findAllByTogglIdIn(any()) }
  }

  @Test
  fun `current totals sum today and the current Monday through Sunday week`() {
    val mondayEntry =
        entry(
            togglId = 10L,
            start = "2026-08-24T15:00:00Z",
            stop = "2026-08-24T16:00:00Z",
            duration = 3_600L,
            description = "Monday work",
        )
    val todayEntry =
        entry(
            togglId = 11L,
            start = "2026-08-26T16:00:00Z",
            stop = "2026-08-26T16:30:05Z",
            duration = 1_805L,
            description = "Wednesday work",
        )
    every {
      timeEntryRepository.findCompletedInRange(
          userId = 42L,
          startInclusive = Instant.parse("2026-08-24T05:00:00Z"),
          endExclusive = Instant.parse("2026-08-27T05:00:00Z"),
      )
    } returns listOf(todayEntry, mondayEntry)

    val totals = service.currentTotals()

    totals shouldBe
        TimeEntryTotalsView(
            todayCompletedSeconds = 1_805L,
            todayCompletedFormatted = "0:30:05",
            weekCompletedSeconds = 5_405L,
            weekCompletedFormatted = "1:30:05",
            todayStart = Instant.parse("2026-08-26T05:00:00Z"),
            weekStart = Instant.parse("2026-08-24T05:00:00Z"),
            endExclusive = Instant.parse("2026-08-27T05:00:00Z"),
        )
  }

  @Test
  fun `entry stays on its start day and unsafe project colors are discarded`() {
    val crossMidnight =
        entry(
            togglId = 4L,
            start = "2026-08-26T04:30:00Z",
            stop = "2026-08-26T05:30:00Z",
            duration = 3_600L,
            description = "Late deploy",
            projectId = 12L,
        )
    every { timeEntryRepository.findCompletedInRange(any(), any(), any()) } returns
        listOf(crossMidnight)
    every { projectRepository.findAllByTogglIdIn(listOf(12L)) } returns
        listOf(
            Project(togglId = 12L, workspaceId = 7L, name = "Ops", color = "red; background: black")
        )
    every { timeEntryRepository.existsCompletedBefore(any(), any()) } returns false

    val page = service.initialPage()

    assertSoftly(page.groups.single()) {
      label shouldBe "Yesterday"
      entries.single().timeRange shouldBe "11:30 PM – 12:30 AM"
      entries.single().projectPicker.projectColor.shouldBeNull()
    }
    verify(exactly = 0) { clientRepository.findAllByTogglIdIn(any()) }
  }

  private fun entry(
      togglId: Long,
      start: String,
      stop: String,
      duration: Long,
      description: String?,
      projectId: Long? = null,
  ) =
      TimeEntry(
          togglId = togglId,
          userId = 42L,
          workspaceId = 7L,
          projectId = projectId,
          description = description,
          start = Instant.parse(start),
          stop = Instant.parse(stop),
          duration = duration,
      )
}
