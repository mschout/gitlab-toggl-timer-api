package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.toggl.CreateProjectRequest as CreateTogglProjectRequest
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class TogglServiceTest {

  private lateinit var togglClient: TogglClient
  private lateinit var service: TogglService

  @BeforeEach
  fun setUp() {
    togglClient = mock(TogglClient::class.java)
    service = TogglService(togglClient)
  }

  @Test
  fun `should return existing project whose name starts with the issue prefix`() {
    val existing = TogglProject(id = 100L, name = "42 - Existing issue", clientId = 5L)
    `when`(togglClient.getProjects(7L, "42")).thenReturn(listOf(existing))

    val result =
        service.findOrCreateProject(
            workspaceId = 7L,
            clientId = 5L,
            issueNumber = 42L,
            issueTitle = "Existing issue",
        )

    assertSame(existing, result)
  }

  @Test
  fun `should ignore projects whose name does not start with the issue prefix`() {
    // "421 -" starts with "42" but not with "42 -" — must not match issue 42.
    val almostMatch = TogglProject(id = 1L, name = "421 - Different issue", clientId = 5L)
    val noMatch = TogglProject(id = 2L, name = "Some other project", clientId = 5L)
    `when`(togglClient.getProjects(7L, "42")).thenReturn(listOf(almostMatch, noMatch))
    val expectedRequest = CreateTogglProjectRequest(name = "42 - New issue", clientId = 5L)
    val created = TogglProject(id = 999L, name = "42 - New issue", clientId = 5L)
    `when`(togglClient.createProject(7L, expectedRequest)).thenReturn(created)

    val result =
        service.findOrCreateProject(
            workspaceId = 7L,
            clientId = 5L,
            issueNumber = 42L,
            issueTitle = "New issue",
        )

    assertSame(created, result)
  }

  @Test
  fun `should ignore projects with null name`() {
    val nullName = TogglProject(id = 1L, name = null, clientId = 5L)
    `when`(togglClient.getProjects(7L, "42")).thenReturn(listOf(nullName))
    val expectedRequest = CreateTogglProjectRequest(name = "42 - Title", clientId = 5L)
    val created = TogglProject(id = 999L, name = "42 - Title", clientId = 5L)
    `when`(togglClient.createProject(7L, expectedRequest)).thenReturn(created)

    val result =
        service.findOrCreateProject(
            workspaceId = 7L,
            clientId = 5L,
            issueNumber = 42L,
            issueTitle = "Title",
        )

    assertSame(created, result)
  }

  @Test
  fun `should create new project with correct name and client when no match found`() {
    `when`(togglClient.getProjects(7L, "42")).thenReturn(emptyList())
    val expectedRequest = CreateTogglProjectRequest(name = "42 - Brand new", clientId = 5L)
    val created = TogglProject(id = 999L, name = "42 - Brand new", clientId = 5L)
    `when`(togglClient.createProject(7L, expectedRequest)).thenReturn(created)

    val result =
        service.findOrCreateProject(
            workspaceId = 7L,
            clientId = 5L,
            issueNumber = 42L,
            issueTitle = "Brand new",
        )

    assertSame(created, result)
    // Verify the exact request was sent (workspaceId, name, clientId, plus default fields).
    verify(togglClient).createProject(7L, expectedRequest)
  }

  @Test
  fun `should pick first matching project when multiple share the prefix`() {
    val first = TogglProject(id = 1L, name = "42 - First", clientId = 5L)
    val second = TogglProject(id = 2L, name = "42 - Second", clientId = 5L)
    `when`(togglClient.getProjects(7L, "42")).thenReturn(listOf(first, second))

    val result =
        service.findOrCreateProject(
            workspaceId = 7L,
            clientId = 5L,
            issueNumber = 42L,
            issueTitle = "Anything",
        )

    assertSame(first, result)
  }

  @Test
  fun `startTimer should throw NotImplementedError until implemented`() {
    val project = TogglProject(id = 1L, name = "42 - X", clientId = 5L)
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
        )

    assertThrows(NotImplementedError::class.java) { service.startTimer(project, request) }
  }
}
