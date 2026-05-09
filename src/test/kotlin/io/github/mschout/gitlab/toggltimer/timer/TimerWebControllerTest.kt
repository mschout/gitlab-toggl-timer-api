package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimerWebControllerTest {

  private lateinit var timerService: TimerService
  private lateinit var controller: TimerWebController

  @BeforeEach
  fun setUp() {
    timerService = mockk()
    controller = TimerWebController(timerService)
  }

  @Test
  fun `index should return timer-index view with welcome message`() {
    val mav = controller.index()

    assertSoftly(mav) {
      viewName shouldBe "timer-index"
      model["message"] shouldBe "Welcome to the Timer Page!"
    }
  }

  @Test
  fun `createProject should delegate to timer service and expose project in the model`() {
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
  fun `startTimer should delegate to timer service and expose start time in the model`() {
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
}
