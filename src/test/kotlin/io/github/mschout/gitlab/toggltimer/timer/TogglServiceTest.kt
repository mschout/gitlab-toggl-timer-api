package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.toggl.CreateProjectRequest as CreateTogglProjectRequest
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TogglServiceTest {

  private lateinit var togglClient: TogglClient
  private lateinit var togglClientFactory: TogglClientFactory
  private lateinit var credentialsService: CurrentUserCredentialsService
  private lateinit var service: TogglService

  @BeforeEach
  fun setUp() {
    togglClient = mockk()
    togglClientFactory = mockk()
    credentialsService = mockk()
    every { credentialsService.requireTogglApiKey() } returns "test-api-key"
    every { togglClientFactory.forApiKey("test-api-key") } returns togglClient
    service = TogglService(togglClientFactory, credentialsService)
  }

  @Test
  fun `should return existing project whose name starts with the issue prefix`() {
    val existing = TogglProject(id = 100L, name = "42 - Existing issue", clientId = 5L)
    every { togglClient.getProjects(7L, "42") } returns listOf(existing)

    val result =
        service.findOrCreateProject(
            workspaceId = 7L,
            clientId = 5L,
            issueNumber = 42L,
            issueTitle = "Existing issue",
        )

    result shouldBeSameInstanceAs existing
  }

  @Test
  fun `should ignore projects whose name does not start with the issue prefix`() {
    // "421 -" starts with "42" but not with "42 -" — must not match issue 42.
    val almostMatch = TogglProject(id = 1L, name = "421 - Different issue", clientId = 5L)
    val noMatch = TogglProject(id = 2L, name = "Some other project", clientId = 5L)
    every { togglClient.getProjects(7L, "42") } returns listOf(almostMatch, noMatch)
    val expectedRequest = CreateTogglProjectRequest(name = "42 - New issue", clientId = 5L)
    val created = TogglProject(id = 999L, name = "42 - New issue", clientId = 5L)
    every { togglClient.createProject(7L, expectedRequest) } returns created

    val result =
        service.findOrCreateProject(
            workspaceId = 7L,
            clientId = 5L,
            issueNumber = 42L,
            issueTitle = "New issue",
        )

    result shouldBeSameInstanceAs created
  }

  @Test
  fun `should ignore projects with null name`() {
    val nullName = TogglProject(id = 1L, name = null, clientId = 5L)
    every { togglClient.getProjects(7L, "42") } returns listOf(nullName)
    val expectedRequest = CreateTogglProjectRequest(name = "42 - Title", clientId = 5L)
    val created = TogglProject(id = 999L, name = "42 - Title", clientId = 5L)
    every { togglClient.createProject(7L, expectedRequest) } returns created

    val result =
        service.findOrCreateProject(
            workspaceId = 7L,
            clientId = 5L,
            issueNumber = 42L,
            issueTitle = "Title",
        )

    result shouldBeSameInstanceAs created
  }

  @Test
  fun `should create new project with correct name and client when no match found`() {
    every { togglClient.getProjects(7L, "42") } returns emptyList()
    val expectedRequest = CreateTogglProjectRequest(name = "42 - Brand new", clientId = 5L)
    val created = TogglProject(id = 999L, name = "42 - Brand new", clientId = 5L)
    every { togglClient.createProject(7L, expectedRequest) } returns created

    val result =
        service.findOrCreateProject(
            workspaceId = 7L,
            clientId = 5L,
            issueNumber = 42L,
            issueTitle = "Brand new",
        )

    result shouldBeSameInstanceAs created
    // Verify the exact request was sent (workspaceId, name, clientId, plus default fields).
    verify { togglClient.createProject(7L, expectedRequest) }
  }

  @Test
  fun `should pick first matching project when multiple share the prefix`() {
    val first = TogglProject(id = 1L, name = "42 - First", clientId = 5L)
    val second = TogglProject(id = 2L, name = "42 - Second", clientId = 5L)
    every { togglClient.getProjects(7L, "42") } returns listOf(first, second)

    val result =
        service.findOrCreateProject(
            workspaceId = 7L,
            clientId = 5L,
            issueNumber = 42L,
            issueTitle = "Anything",
        )

    result shouldBeSameInstanceAs first
  }

  @Test
  fun `fetchWorkspaces uses the supplied api key directly`() {
    val freshClient = mockk<TogglClient>()
    every { togglClientFactory.forApiKey("brand-new-key") } returns freshClient
    val workspaces =
        listOf(TogglWorkspace(id = 1L, name = "Alpha"), TogglWorkspace(id = 2L, name = "Beta"))
    every { freshClient.getWorkspaces() } returns workspaces

    service.fetchWorkspaces("brand-new-key") shouldBe workspaces

    verify { togglClientFactory.forApiKey("brand-new-key") }
    verify(exactly = 0) { credentialsService.requireTogglApiKey() }
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

    shouldThrow<NotImplementedError> { service.startTimer(project, request) }
  }
}
