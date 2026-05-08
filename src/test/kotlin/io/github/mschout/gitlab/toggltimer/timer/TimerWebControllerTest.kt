package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class TimerWebControllerTest {

  private lateinit var timerService: TimerService
  private lateinit var controller: TimerWebController

  @BeforeEach
  fun setUp() {
    timerService = mock(TimerService::class.java)
    controller = TimerWebController(timerService)
  }

  @Test
  fun `index should return timer-index view with welcome message`() {
    val mav = controller.index()

    assertEquals("timer-index", mav.viewName)
    assertEquals("Welcome to the Timer Page!", mav.model["message"])
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
    `when`(timerService.createProject(expectedRequest)).thenReturn(project)

    val mav =
        controller.createProject(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
        )

    assertEquals("create-project", mav.viewName)
    assertSame(project, mav.model["project"])
    verify(timerService).createProject(expectedRequest)
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
    `when`(timerService.startTimer(expectedRequest)).thenReturn(startInstant)

    val mav =
        controller.startTimer(
            issueUrl = "https://gitlab.com/g/p/-/issues/99",
            workspaceId = 11L,
            clientId = 22L,
        )

    assertEquals("start-timer", mav.viewName)
    assertEquals(startInstant, mav.model["startTime"])
    verify(timerService).startTimer(expectedRequest)
  }
}
