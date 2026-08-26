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

import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspaceClient
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.ui.ExtendedModelMap
import org.springframework.validation.BeanPropertyBindingResult

class TimerWebControllerTest {

  private lateinit var timerService: TimerService
  private lateinit var credentialsService: CurrentUserCredentialsService
  private lateinit var togglService: TogglService
  private lateinit var timeEntryHistoryService: TimeEntryHistoryService
  private lateinit var timeEntryDescriptionService: TimeEntryDescriptionService
  private lateinit var timeEntryProjectService: TimeEntryProjectService
  private lateinit var controller: TimerWebController

  @BeforeEach
  fun setUp() {
    timerService = mockk()
    credentialsService = mockk()
    togglService = mockk()
    timeEntryHistoryService = mockk()
    timeEntryDescriptionService = mockk()
    timeEntryProjectService = mockk()
    every { credentialsService.currentTogglWorkspaceId() } returns null
    every { togglService.fetchWorkspaces() } returns emptyList()
    every { togglService.fetchClients(any()) } returns emptyList()
    every { togglService.getCurrentRunningTimer() } returns null
    every { timeEntryHistoryService.initialPage() } returns emptyHistoryPage()
    controller =
        TimerWebController(
            timerService,
            credentialsService,
            togglService,
            timeEntryHistoryService,
            timeEntryDescriptionService,
            timeEntryProjectService,
        )
  }

  @Test
  fun `index should return timer-index view with welcome message and empty form`() {
    val model = ExtendedModelMap()

    val view = controller.index(model)

    assertSoftly {
      view shouldBe "timer-index"
      model["message"] shouldBe "Welcome to the Timer Page!"
      model["form"] shouldBe TimerForm()
      model["historyPage"] shouldBe emptyHistoryPage()
    }
  }

  @Test
  fun `recent entries returns the refreshed history section`() {
    val historyPage = emptyHistoryPage()
    every { timeEntryHistoryService.initialPage() } returns historyPage
    val model = ExtendedModelMap()

    val view = controller.recentEntries(model)

    view shouldBe "timer-index :: recent-entries"
    model["historyPage"] shouldBeSameInstanceAs historyPage
  }

  @Test
  fun `older entries returns the preceding history page`() {
    val before = LocalDate.parse("2026-08-20")
    val historyPage = emptyHistoryPage().copy(initial = false)
    every { timeEntryHistoryService.pageBefore(before) } returns historyPage
    val model = ExtendedModelMap()

    val view = controller.olderEntries(before = before, model = model)

    view shouldBe "timer-index :: history-page"
    model["historyPage"] shouldBeSameInstanceAs historyPage
  }

  @Test
  fun `description update returns the reusable editor fragment`() {
    val editor = TimeEntryDescriptionEditorView(togglId = 123L, description = "Updated")
    every { timeEntryDescriptionService.updateDescription(123L, "Updated") } returns editor
    val model = ExtendedModelMap()

    val view =
        controller.updateEntryDescription(togglId = 123L, description = "Updated", model = model)

    view shouldBe "fragments/time-entry-description :: description-editor"
    model["descriptionEditor"] shouldBeSameInstanceAs editor
  }

  @Test
  fun `description update preserves typed text when Toggl fails`() {
    every { timeEntryDescriptionService.updateDescription(123L, "Still typed") } throws
        TogglDescriptionUpdateException(RuntimeException("down"))
    val model = ExtendedModelMap()

    val view =
        controller.updateEntryDescription(
            togglId = 123L,
            description = "Still typed",
            model = model,
        )

    view shouldBe "fragments/time-entry-description :: description-editor"
    model["descriptionEditor"] shouldBe
        TimeEntryDescriptionEditorView(
            togglId = 123L,
            description = "Still typed",
            error = "Could not save to Toggl. Press Enter to retry.",
            editing = true,
        )
  }

  @Test
  fun `description update reports when only local history fails`() {
    every { timeEntryDescriptionService.updateDescription(123L, "Saved remotely") } throws
        TimeEntryHistoryUpdateException(RuntimeException("database down"))
    val model = ExtendedModelMap()

    controller.updateEntryDescription(togglId = 123L, description = "Saved remotely", model = model)

    model["descriptionEditor"] shouldBe
        TimeEntryDescriptionEditorView(
            togglId = 123L,
            description = "Saved remotely",
            error = "Saved to Toggl, but local history is out of sync. Press Enter to retry.",
            editing = true,
        )
  }

  @Test
  fun `description update leaves inaccessible entries as not found`() {
    every { timeEntryDescriptionService.updateDescription(999L, any()) } throws
        TimeEntryNotFoundException(999L)

    shouldThrow<TimeEntryNotFoundException> {
      controller.updateEntryDescription(
          togglId = 999L,
          description = "No access",
          model = ExtendedModelMap(),
      )
    }
  }

  @Test
  fun `project search returns the reusable results fragment`() {
    val search =
        TimeEntryProjectSearchView(
            togglId = 123L,
            query = "Indiana",
            projects =
                listOf(
                    TimeEntryProjectSearchResultView(
                        togglId = 200L,
                        name = "74393 - Indiana",
                        clientName = "Inforuptcy",
                        color = "#4C6EF5",
                        selected = false,
                    )
                ),
        )
    every { timeEntryProjectService.searchProjects(123L, "Indiana") } returns search
    val model = ExtendedModelMap()

    val view = controller.searchEntryProjects(togglId = 123L, query = "Indiana", model = model)

    view shouldBe "fragments/time-entry-project :: project-results"
    model["projectSearch"] shouldBeSameInstanceAs search
  }

  @Test
  fun `project search returns an inline error when Postgres search fails`() {
    every { timeEntryProjectService.searchProjects(123L, "Indiana") } throws
        RuntimeException("database down")
    val model = ExtendedModelMap()

    controller.searchEntryProjects(togglId = 123L, query = "Indiana", model = model)

    model["projectSearch"] shouldBe
        TimeEntryProjectSearchView(
            togglId = 123L,
            query = "Indiana",
            projects = emptyList(),
            error = "Could not search projects. Try again.",
        )
  }

  @Test
  fun `project search leaves inaccessible entries as not found`() {
    every { timeEntryProjectService.searchProjects(999L, any()) } throws
        TimeEntryProjectNotFoundException()

    shouldThrow<TimeEntryProjectNotFoundException> {
      controller.searchEntryProjects(togglId = 999L, query = "Indiana", model = ExtendedModelMap())
    }
  }

  @Test
  fun `project update returns the reusable picker fragment`() {
    val picker =
        TimeEntryProjectPickerView(
            togglId = 123L,
            projectName = "74393 - Indiana",
            clientName = "Inforuptcy",
            projectColor = "#4C6EF5",
        )
    every { timeEntryProjectService.updateProject(123L, 200L) } returns picker
    val model = ExtendedModelMap()

    val view = controller.updateEntryProject(togglId = 123L, projectId = 200L, model = model)

    view shouldBe "fragments/time-entry-project :: project-picker"
    model["projectPicker"] shouldBeSameInstanceAs picker
  }

  @Test
  fun `project update reopens picker when Toggl fails`() {
    val current =
        TimeEntryProjectPickerView(
            togglId = 123L,
            projectName = "Old project",
            clientName = "Inforuptcy",
            projectColor = "#4C6EF5",
        )
    every { timeEntryProjectService.updateProject(123L, 200L) } throws
        TogglProjectUpdateException(RuntimeException("down"))
    every { timeEntryProjectService.currentPicker(123L) } returns current
    val model = ExtendedModelMap()

    controller.updateEntryProject(togglId = 123L, projectId = 200L, model = model)

    model["projectPicker"] shouldBe
        current.copy(error = "Could not save to Toggl. Choose a project to retry.", open = true)
  }

  @Test
  fun `project update reports when only local history fails`() {
    val current =
        TimeEntryProjectPickerView(
            togglId = 123L,
            projectName = "Old project",
            clientName = "Inforuptcy",
            projectColor = "#4C6EF5",
        )
    every { timeEntryProjectService.updateProject(123L, 200L) } throws
        TimeEntryProjectHistoryUpdateException(RuntimeException("database down"))
    every { timeEntryProjectService.currentPicker(123L) } returns current
    val model = ExtendedModelMap()

    controller.updateEntryProject(togglId = 123L, projectId = 200L, model = model)

    model["projectPicker"] shouldBe
        current.copy(
            error = "Saved to Toggl, but local history is out of sync. Choose a project to retry.",
            open = true,
        )
  }

  @Test
  fun `index pre-fills workspaceId from saved user settings`() {
    every { credentialsService.currentTogglWorkspaceId() } returns 99L
    val model = ExtendedModelMap()

    controller.index(model)

    (model["form"] as TimerForm).workspaceId shouldBe 99L
  }

  @Test
  fun `index exposes running timer fields when a Toggl timer is already running`() {
    val startInstant = Instant.parse("2026-05-15T10:00:00Z")
    every { togglService.getCurrentRunningTimer() } returns
        StartTimerResult(
            startTime = startInstant,
            projectName = "42 - Some issue",
            description = "in progress",
        )
    val model = ExtendedModelMap()

    val view = controller.index(model)

    assertSoftly {
      view shouldBe "timer-index"
      model["startTime"] shouldBe startInstant
      model["projectName"] shouldBe "42 - Some issue"
      model["description"] shouldBe "in progress"
    }
  }

  @Test
  fun `index does not expose running timer fields when no timer is running`() {
    val model = ExtendedModelMap()

    controller.index(model)

    model.containsAttribute("startTime") shouldBe false
    model.containsAttribute("projectName") shouldBe false
    model.containsAttribute("description") shouldBe false
  }

  @Test
  fun `index does not crash when getCurrentRunningTimer throws`() {
    every { togglService.getCurrentRunningTimer() } throws RuntimeException("toggl down")
    val model = ExtendedModelMap()

    val view = controller.index(model)

    view shouldBe "timer-index"
    model.containsAttribute("startTime") shouldBe false
  }

  @Test
  fun `index should preserve a pre-existing form attribute`() {
    val existing =
        TimerForm(issueUrl = "https://gitlab.com/g/p/-/issues/1", workspaceId = 1L, clientId = 2L)
    val model = ExtendedModelMap().apply { addAttribute("form", existing) }

    controller.index(model)

    model["form"] shouldBeSameInstanceAs existing
  }

  @Test
  fun `index loads workspaces and clients into the model when fetch succeeds`() {
    every { credentialsService.currentTogglWorkspaceId() } returns 7L
    val workspaces = listOf(TogglWorkspace(id = 7L, name = "Acme"))
    val clients = listOf(TogglWorkspaceClient(id = 5L, name = "Globex"))
    every { togglService.fetchWorkspaces() } returns workspaces
    every { togglService.fetchClients(7L) } returns clients
    val model = ExtendedModelMap()

    controller.index(model)

    assertSoftly {
      model["workspaces"] shouldBe workspaces
      model["clients"] shouldBe clients
      model["togglFetchError"].shouldBeNull()
      model["clientsFetchError"].shouldBeNull()
    }
  }

  @Test
  fun `index sets togglFetchError when fetchWorkspaces throws`() {
    every { togglService.fetchWorkspaces() } throws RuntimeException("boom")
    val model = ExtendedModelMap()

    controller.index(model)

    assertSoftly {
      (model["workspaces"] as List<*>).shouldBeEmpty()
      (model["clients"] as List<*>).shouldBeEmpty()
      model["togglFetchError"] shouldBe
          "Could not load Toggl workspaces — check that your API key is valid."
    }
    verify(exactly = 0) { togglService.fetchClients(any()) }
  }

  @Test
  fun `index does not call fetchClients when form has no workspaceId`() {
    every { togglService.fetchWorkspaces() } returns listOf(TogglWorkspace(id = 1L, name = "Acme"))
    val model = ExtendedModelMap()

    controller.index(model)

    (model["clients"] as List<*>).shouldBeEmpty()
    verify(exactly = 0) { togglService.fetchClients(any()) }
  }

  @Test
  fun `index sets clientsFetchError when fetchClients throws`() {
    every { credentialsService.currentTogglWorkspaceId() } returns 7L
    every { togglService.fetchWorkspaces() } returns listOf(TogglWorkspace(id = 7L, name = "Acme"))
    every { togglService.fetchClients(7L) } throws RuntimeException("boom")
    val model = ExtendedModelMap()

    controller.index(model)

    assertSoftly {
      (model["clients"] as List<*>).shouldBeEmpty()
      model["clientsFetchError"] shouldBe
          "Could not load Toggl clients — check that your API key is valid."
      model["togglFetchError"].shouldBeNull()
    }
  }

  @Test
  fun `clientsFragment returns timer-index client-select with clients for the workspace`() {
    val clients = listOf(TogglWorkspaceClient(id = 5L, name = "Globex"))
    every { togglService.fetchClients(7L) } returns clients
    val model = ExtendedModelMap()

    val view = controller.clientsFragment(workspaceId = 7L, model = model)

    assertSoftly {
      view shouldBe "timer-index :: client-select"
      model["clients"] shouldBe clients
      model["clientsFetchError"].shouldBeNull()
    }
  }

  @Test
  fun `clientsFragment surfaces clientsFetchError when fetchClients throws`() {
    every { togglService.fetchClients(7L) } throws RuntimeException("boom")
    val model = ExtendedModelMap()

    val view = controller.clientsFragment(workspaceId = 7L, model = model)

    assertSoftly {
      view shouldBe "timer-index :: client-select"
      (model["clients"] as List<*>).shouldBeEmpty()
      model["clientsFetchError"] shouldBe
          "Could not load Toggl clients — check that your API key is valid."
    }
  }

  @Test
  fun `clientsFragment returns empty clients when workspaceId is null`() {
    val model = ExtendedModelMap()

    val view = controller.clientsFragment(workspaceId = null, model = model)

    assertSoftly {
      view shouldBe "timer-index :: client-select"
      (model["clients"] as List<*>).shouldBeEmpty()
      model["clientsFetchError"].shouldBeNull()
    }
    verify(exactly = 0) { togglService.fetchClients(any()) }
  }

  @Test
  fun `createProject GET should delegate to timer service and expose project in the model`() {
    val project = TogglProject(id = 100L, name = "42 - Some issue", clientId = 5L)
    val expectedRequest =
        CreateProjectRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
        )
    every { timerService.createProject(expectedRequest) } returns project

    val mav =
        controller.createProject(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
        )

    assertSoftly(mav) {
      viewName shouldBe "create-project"
      model["project"] shouldBeSameInstanceAs project
    }
    verify { timerService.createProject(expectedRequest) }
  }

  @Test
  fun `startTimer GET should delegate to timer service and expose result fields in the model`() {
    val startInstant = Instant.parse("2026-05-08T15:30:00Z")
    val expectedRequest =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/99",
            workspaceId = 11L,
            clientId = 22L,
        )
    val timerResult =
        StartTimerResult(
            startTime = startInstant,
            projectName = "99 - Some issue",
            description = "tracking",
        )
    every { timerService.startTimer(expectedRequest) } returns timerResult

    val mav =
        controller.startTimer(
            issueUrl = "https://gitlab.com/g/p/-/issues/99",
            workspaceId = 11L,
            clientId = 22L,
        )

    assertSoftly(mav) {
      viewName shouldBe "start-timer"
      model["startTime"] shouldBe startInstant
      model["projectName"] shouldBe "99 - Some issue"
      model["description"] shouldBe "tracking"
    }
    verify { timerService.startTimer(expectedRequest) }
  }

  @Test
  fun `createProject POST returns full view when not an HTMX request`() {
    val form = validForm()
    val project = TogglProject(id = 100L, name = "42 - Some issue", clientId = 5L)
    every { timerService.createProject(form.toCreateProjectRequest()) } returns project
    val model = ExtendedModelMap()
    val response = MockHttpServletResponse()

    val view =
        controller.createProjectSubmit(
            form = form,
            bindingResult = bindingResult(form),
            hxRequest = false,
            model = model,
            response = response,
        )

    assertSoftly {
      view shouldBe "create-project"
      model["project"] shouldBeSameInstanceAs project
      response.getHeader("HX-Retarget").shouldBeNull()
    }
  }

  @Test
  fun `createProject POST returns fragment view when HTMX request`() {
    val form = validForm()
    val project = TogglProject(id = 100L, name = "42 - Some issue", clientId = 5L)
    every { timerService.createProject(form.toCreateProjectRequest()) } returns project
    val model = ExtendedModelMap()
    val response = MockHttpServletResponse()

    val view =
        controller.createProjectSubmit(
            form = form,
            bindingResult = bindingResult(form),
            hxRequest = true,
            model = model,
            response = response,
        )

    view shouldBe "create-project :: result-card"
    model["project"] shouldBeSameInstanceAs project
  }

  @Test
  fun `createProject POST with binding errors returns form fragment with retarget headers when HTMX`() {
    val form = TimerForm()
    val errors = bindingResult(form).apply { rejectValue("issueUrl", "NotBlank") }
    val model = ExtendedModelMap()
    val response = MockHttpServletResponse()

    val view =
        controller.createProjectSubmit(
            form = form,
            bindingResult = errors,
            hxRequest = true,
            model = model,
            response = response,
        )

    assertSoftly {
      view shouldBe "timer-index :: timer-form"
      response.getHeader("HX-Retarget") shouldBe "#timer-form-card"
      response.getHeader("HX-Reswap") shouldBe "outerHTML"
      model["message"] shouldBe "Welcome to the Timer Page!"
      model["workspaces"] shouldBe emptyList<TogglWorkspace>()
      model["clients"] shouldBe emptyList<TogglWorkspaceClient>()
    }
    verify(exactly = 0) { timerService.createProject(any()) }
  }

  @Test
  fun `createProject POST with binding errors returns full index view when not HTMX`() {
    val form = TimerForm()
    val errors = bindingResult(form).apply { rejectValue("issueUrl", "NotBlank") }
    val model = ExtendedModelMap()
    val response = MockHttpServletResponse()

    val view =
        controller.createProjectSubmit(
            form = form,
            bindingResult = errors,
            hxRequest = false,
            model = model,
            response = response,
        )

    assertSoftly {
      view shouldBe "timer-index"
      response.getHeader("HX-Retarget").shouldBeNull()
      model["message"] shouldBe "Welcome to the Timer Page!"
      model["workspaces"] shouldBe emptyList<TogglWorkspace>()
    }
    verify(exactly = 0) { timerService.createProject(any()) }
  }

  @Test
  fun `startTimer POST returns full view when not an HTMX request`() {
    val form = validForm(description = "tracking")
    val startInstant = Instant.parse("2026-05-08T15:30:00Z")
    val timerResult =
        StartTimerResult(
            startTime = startInstant,
            projectName = "42 - Some issue",
            description = "tracking",
        )
    every { timerService.startTimer(form.toStartTimerRequest()) } returns timerResult
    val model = ExtendedModelMap()
    val response = MockHttpServletResponse()

    val view =
        controller.startTimerSubmit(
            form = form,
            bindingResult = bindingResult(form),
            hxRequest = false,
            model = model,
            response = response,
        )

    assertSoftly {
      view shouldBe "start-timer"
      model["startTime"] shouldBe startInstant
      model["projectName"] shouldBe "42 - Some issue"
      model["description"] shouldBe "tracking"
      response.getHeader("HX-Retarget").shouldBeNull()
    }
  }

  @Test
  fun `startTimer POST returns fragment view when HTMX request`() {
    val form = validForm()
    val startInstant = Instant.parse("2026-05-08T15:30:00Z")
    val timerResult =
        StartTimerResult(
            startTime = startInstant,
            projectName = "42 - Some issue",
            description = null,
        )
    every { timerService.startTimer(form.toStartTimerRequest()) } returns timerResult
    val model = ExtendedModelMap()
    val response = MockHttpServletResponse()

    val view =
        controller.startTimerSubmit(
            form = form,
            bindingResult = bindingResult(form),
            hxRequest = true,
            model = model,
            response = response,
        )

    view shouldBe "start-timer :: result-card"
    model["startTime"] shouldBe startInstant
    model["projectName"] shouldBe "42 - Some issue"
    model["description"].shouldBeNull()
  }

  @Test
  fun `startTimer POST with binding errors returns form fragment with retarget headers when HTMX`() {
    val form = TimerForm()
    val errors = bindingResult(form).apply { rejectValue("workspaceId", "NotNull") }
    val model = ExtendedModelMap()
    val response = MockHttpServletResponse()

    val view =
        controller.startTimerSubmit(
            form = form,
            bindingResult = errors,
            hxRequest = true,
            model = model,
            response = response,
        )

    assertSoftly {
      view shouldBe "timer-index :: timer-form"
      response.getHeader("HX-Retarget") shouldBe "#timer-form-card"
      response.getHeader("HX-Reswap") shouldBe "outerHTML"
      model["workspaces"] shouldBe emptyList<TogglWorkspace>()
    }
    verify(exactly = 0) { timerService.startTimer(any()) }
  }

  @Test
  fun `stopTimer POST returns full view when not HTMX with running timer`() {
    every { timerService.stopTimer() } returns
        StopTimerResult(durationSeconds = 125L, durationFormatted = "00:02:05")
    val model = ExtendedModelMap()
    val response = MockHttpServletResponse()

    val view = controller.stopTimerSubmit(hxRequest = false, model = model, response = response)

    assertSoftly {
      view shouldBe "stop-timer"
      model["durationFormatted"] shouldBe "00:02:05"
      model["stopped"] shouldBe true
      response.getHeader("HX-Trigger").shouldBeNull()
    }
  }

  @Test
  fun `stopTimer POST returns fragment view when HTMX with running timer`() {
    every { timerService.stopTimer() } returns
        StopTimerResult(durationSeconds = 3661L, durationFormatted = "01:01:01")
    val model = ExtendedModelMap()
    val response = MockHttpServletResponse()

    val view = controller.stopTimerSubmit(hxRequest = true, model = model, response = response)

    assertSoftly {
      view shouldBe "stop-timer :: result-card"
      model["durationFormatted"] shouldBe "01:01:01"
      model["stopped"] shouldBe true
      response.getHeader("HX-Trigger") shouldBe "timeEntriesChanged"
    }
  }

  @Test
  fun `stopTimer POST sets stopped=false when no timer was running`() {
    every { timerService.stopTimer() } returns null
    val model = ExtendedModelMap()
    val response = MockHttpServletResponse()

    val view = controller.stopTimerSubmit(hxRequest = true, model = model, response = response)

    view shouldBe "stop-timer :: result-card"
    model["stopped"] shouldBe false
    model.containsAttribute("durationFormatted") shouldBe false
    response.getHeader("HX-Trigger").shouldBeNull()
  }

  private fun emptyHistoryPage() =
      TimeEntryHistoryPage(
          groups = emptyList(),
          rangeLabel = "Aug 20–Aug 26, 2026",
          nextBefore = null,
          initial = true,
      )

  private fun validForm(description: String? = null) =
      TimerForm(
          issueUrl = "https://gitlab.com/g/p/-/issues/42",
          workspaceId = 7L,
          clientId = 5L,
          description = description,
      )

  private fun bindingResult(form: TimerForm) = BeanPropertyBindingResult(form, "form")
}
