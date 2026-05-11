package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.ui.ExtendedModelMap
import org.springframework.validation.BeanPropertyBindingResult

class TimerWebControllerTest {

  private lateinit var timerService: TimerService
  private lateinit var credentialsService: CurrentUserCredentialsService
  private lateinit var controller: TimerWebController

  @BeforeEach
  fun setUp() {
    timerService = mockk()
    credentialsService = mockk()
    every { credentialsService.currentTogglWorkspaceId() } returns null
    controller = TimerWebController(timerService, credentialsService)
  }

  @Test
  fun `index should return timer-index view with welcome message and empty form`() {
    val model = ExtendedModelMap()

    val view = controller.index(model)

    assertSoftly {
      view shouldBe "timer-index"
      model["message"] shouldBe "Welcome to the Timer Page!"
      model["form"] shouldBe TimerForm()
    }
  }

  @Test
  fun `index pre-fills workspaceId from saved user settings`() {
    every { credentialsService.currentTogglWorkspaceId() } returns 99L
    val model = ExtendedModelMap()

    controller.index(model)

    (model["form"] as TimerForm).workspaceId shouldBe 99L
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
  fun `startTimer GET should delegate to timer service and expose start time in the model`() {
    val startInstant = Instant.parse("2026-05-08T15:30:00Z")
    val expectedRequest =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/99",
            workspaceId = 11L,
            clientId = 22L,
        )
    every { timerService.startTimer(expectedRequest) } returns startInstant

    val mav =
        controller.startTimer(
            issueUrl = "https://gitlab.com/g/p/-/issues/99",
            workspaceId = 11L,
            clientId = 22L,
        )

    assertSoftly(mav) {
      viewName shouldBe "start-timer"
      model["startTime"] shouldBe startInstant
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
    }
    verify(exactly = 0) { timerService.createProject(any()) }
  }

  @Test
  fun `startTimer POST returns full view when not an HTMX request`() {
    val form = validForm(description = "tracking")
    val startInstant = Instant.parse("2026-05-08T15:30:00Z")
    every { timerService.startTimer(form.toStartTimerRequest()) } returns startInstant
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
      response.getHeader("HX-Retarget").shouldBeNull()
    }
  }

  @Test
  fun `startTimer POST returns fragment view when HTMX request`() {
    val form = validForm()
    val startInstant = Instant.parse("2026-05-08T15:30:00Z")
    every { timerService.startTimer(form.toStartTimerRequest()) } returns startInstant
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
    }
    verify(exactly = 0) { timerService.startTimer(any()) }
  }

  private fun validForm(description: String? = null) =
      TimerForm(
          issueUrl = "https://gitlab.com/g/p/-/issues/42",
          workspaceId = 7L,
          clientId = 5L,
          description = description,
      )

  private fun bindingResult(form: TimerForm) = BeanPropertyBindingResult(form, "form")
}
