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

import io.github.mschout.gitlab.toggltimer.toggl.CreateProjectRequest as CreateTogglProjectRequest
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspaceClient
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
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
  fun `fetchWorkspaces (no-arg) uses the saved api key from credentialsService`() {
    val workspaces =
        listOf(TogglWorkspace(id = 1L, name = "Alpha"), TogglWorkspace(id = 2L, name = "Beta"))
    every { togglClient.getWorkspaces() } returns workspaces

    service.fetchWorkspaces() shouldBe workspaces

    verify { credentialsService.requireTogglApiKey() }
    verify { togglClientFactory.forApiKey("test-api-key") }
  }

  @Test
  fun `fetchClients returns clients from Toggl using the saved api key`() {
    val clients =
        listOf(
            TogglWorkspaceClient(id = 10L, name = "Globex"),
            TogglWorkspaceClient(id = 11L, name = "Initech"),
        )
    every { togglClient.getClients(7L) } returns clients

    service.fetchClients(7L) shouldBe clients

    verify { credentialsService.requireTogglApiKey() }
    verify { togglClientFactory.forApiKey("test-api-key") }
    verify { togglClient.getClients(7L) }
  }

  @Test
  fun `fetchClients propagates exceptions from the http client`() {
    every { togglClient.getClients(7L) } throws RuntimeException("toggl down")

    shouldThrow<RuntimeException> { service.fetchClients(7L) }
  }

  @Test
  fun `startTimer creates a new running entry when no timer is running`() {
    val project = TogglProject(id = 99L, name = "42 - X", clientId = 5L)
    val explicitStart = Instant.parse("2026-05-15T12:00:00Z")
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
            start = explicitStart,
            description = "hacking on 42",
        )
    every { togglClient.getCurrentTimeEntry() } returns null
    val captured = slot<TogglTimeEntry>()
    every { togglClient.createTimeEntry(7L, capture(captured)) } answers
        {
          captured.captured.copy(id = 555L)
        }

    val result = service.startTimer(project, request)

    result.startTime shouldBe explicitStart
    result.projectName shouldBe "42 - X"
    result.description shouldBe "hacking on 42"
    captured.captured.workspaceId shouldBe 7L
    captured.captured.projectId shouldBe 99L
    captured.captured.start shouldBe explicitStart
    captured.captured.description shouldBe "hacking on 42"
    captured.captured.duration shouldBe -1L
    captured.captured.createdWith shouldBe "Gitlab Toggl Timer"
    verify(exactly = 0) { togglClient.updateTimeEntry(any(), any(), any()) }
  }

  @Test
  fun `startTimer uses Instant now when start is not provided and no timer is running`() {
    val project = TogglProject(id = 99L, name = "42 - X", clientId = 5L)
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
        )
    every { togglClient.getCurrentTimeEntry() } returns null
    val captured = slot<TogglTimeEntry>()
    every { togglClient.createTimeEntry(7L, capture(captured)) } answers
        {
          captured.captured.copy(id = 555L)
        }

    val before = Instant.now()
    val result = service.startTimer(project, request)
    val after = Instant.now()

    result.startTime.shouldNotBeNull()
    (result.startTime >= before) shouldBe true
    (result.startTime <= after) shouldBe true
    result.projectName shouldBe "42 - X"
    result.description shouldBe null
    captured.captured.start shouldBe result.startTime
    captured.captured.description shouldBe null
  }

  @Test
  fun `startTimer assigns project to a running entry that has no project`() {
    val project = TogglProject(id = 99L, name = "42 - X", clientId = 5L)
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
        )
    val existingStart = Instant.parse("2026-05-15T10:00:00Z")
    val running =
        TogglTimeEntry(
            workspaceId = 7L,
            projectId = null,
            start = existingStart,
            description = "already going",
            duration = -1L,
            createdWith = "Toggl Web",
            id = 1234L,
        )
    every { togglClient.getCurrentTimeEntry() } returns running
    val captured = slot<TogglTimeEntry>()
    every { togglClient.updateTimeEntry(7L, 1234L, capture(captured)) } answers
        {
          captured.captured
        }

    val result = service.startTimer(project, request)

    result.startTime shouldBe existingStart
    result.projectName shouldBe "42 - X"
    result.description shouldBe "already going"
    captured.captured.workspaceId shouldBe 7L
    captured.captured.projectId shouldBe 99L
    captured.captured.start shouldBe existingStart
    captured.captured.description shouldBe "already going"
    captured.captured.id shouldBe 1234L
    captured.captured.createdWith shouldBe "Toggl Web"
    verify(exactly = 0) { togglClient.createTimeEntry(any(), any()) }
  }

  @Test
  fun `startTimer overwrites description on running entry when form provides one`() {
    val project = TogglProject(id = 99L, name = "42 - X", clientId = 5L)
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
            description = "fresh description",
        )
    val running =
        TogglTimeEntry(
            workspaceId = 7L,
            projectId = null,
            start = Instant.parse("2026-05-15T10:00:00Z"),
            description = "stale",
            duration = -1L,
            createdWith = "Toggl Web",
            id = 1234L,
        )
    every { togglClient.getCurrentTimeEntry() } returns running
    val captured = slot<TogglTimeEntry>()
    every { togglClient.updateTimeEntry(7L, 1234L, capture(captured)) } answers
        {
          captured.captured
        }

    service.startTimer(project, request)

    captured.captured.description shouldBe "fresh description"
  }

  @Test
  fun `startTimer keeps existing description on running entry when form description is blank`() {
    val project = TogglProject(id = 99L, name = "42 - X", clientId = 5L)
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
            description = "   ",
        )
    val running =
        TogglTimeEntry(
            workspaceId = 7L,
            projectId = null,
            start = Instant.parse("2026-05-15T10:00:00Z"),
            description = "keep me",
            duration = -1L,
            createdWith = "Toggl Web",
            id = 1234L,
        )
    every { togglClient.getCurrentTimeEntry() } returns running
    val captured = slot<TogglTimeEntry>()
    every { togglClient.updateTimeEntry(7L, 1234L, capture(captured)) } answers
        {
          captured.captured
        }

    service.startTimer(project, request)

    captured.captured.description shouldBe "keep me"
  }

  @Test
  fun `startTimer is a no-op when running entry already has a project`() {
    val project = TogglProject(id = 99L, name = "42 - X", clientId = 5L)
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
        )
    val existingStart = Instant.parse("2026-05-15T09:30:00Z")
    val running =
        TogglTimeEntry(
            workspaceId = 7L,
            projectId = 88L,
            start = existingStart,
            description = "doing something else",
            duration = -1L,
            createdWith = "Toggl Web",
            id = 1234L,
        )
    val runningProject = TogglProject(id = 88L, name = "88 - Other work", clientId = 5L)
    every { togglClient.getCurrentTimeEntry() } returns running
    every { togglClient.getProject(7L, 88L) } returns runningProject

    val result = service.startTimer(project, request)

    result.startTime shouldBe existingStart
    result.projectName shouldBe "88 - Other work"
    result.description shouldBe "doing something else"
    verify { togglClient.getProject(7L, 88L) }
    verify(exactly = 0) { togglClient.createTimeEntry(any(), any()) }
    verify(exactly = 0) { togglClient.updateTimeEntry(any(), any(), any()) }
  }

  @Test
  fun `stopRunningTimer returns null when no timer is running`() {
    every { togglClient.getCurrentTimeEntry() } returns null

    service.stopRunningTimer().shouldBeNull()

    verify(exactly = 0) { togglClient.stopTimeEntry(any(), any()) }
  }

  @Test
  fun `stopRunningTimer stops the running entry and returns formatted elapsed duration`() {
    val running =
        TogglTimeEntry(
            workspaceId = 7L,
            projectId = 88L,
            start = Instant.now().minusSeconds(125),
            description = "going",
            duration = -1L,
            createdWith = "Toggl Web",
            id = 1234L,
        )
    every { togglClient.getCurrentTimeEntry() } returns running
    every { togglClient.stopTimeEntry(7L, 1234L) } returns running.copy(duration = 125L)

    val result = service.stopRunningTimer()

    result.shouldNotBeNull()
    (result.durationSeconds >= 125L) shouldBe true
    (result.durationSeconds < 130L) shouldBe true
    result.durationFormatted shouldBe StopTimerResult.formatHms(result.durationSeconds)
    verify { togglClient.stopTimeEntry(7L, 1234L) }
  }

  @Test
  fun `formatHms zero-pads hours minutes and seconds`() {
    StopTimerResult.formatHms(0L) shouldBe "00:00:00"
    StopTimerResult.formatHms(59L) shouldBe "00:00:59"
    StopTimerResult.formatHms(60L) shouldBe "00:01:00"
    StopTimerResult.formatHms(3661L) shouldBe "01:01:01"
    StopTimerResult.formatHms(36000L) shouldBe "10:00:00"
  }

  @Test
  fun `getCurrentRunningTimer returns null when no timer is running`() {
    every { togglClient.getCurrentTimeEntry() } returns null

    service.getCurrentRunningTimer().shouldBeNull()

    verify(exactly = 0) { togglClient.getProject(any(), any()) }
  }

  @Test
  fun `getCurrentRunningTimer returns running entry with project name`() {
    val startInstant = Instant.parse("2026-05-15T10:00:00Z")
    val running =
        TogglTimeEntry(
            workspaceId = 7L,
            projectId = 88L,
            start = startInstant,
            description = "currently going",
            duration = -1L,
            createdWith = "Toggl Web",
            id = 1234L,
        )
    val runningProject = TogglProject(id = 88L, name = "88 - Some project", clientId = 5L)
    every { togglClient.getCurrentTimeEntry() } returns running
    every { togglClient.getProject(7L, 88L) } returns runningProject

    val result = service.getCurrentRunningTimer()

    result.shouldNotBeNull()
    result.startTime shouldBe startInstant
    result.projectName shouldBe "88 - Some project"
    result.description shouldBe "currently going"
    verify { togglClient.getProject(7L, 88L) }
  }

  @Test
  fun `getCurrentRunningTimer returns null projectName when entry has no projectId`() {
    val startInstant = Instant.parse("2026-05-15T10:00:00Z")
    val running =
        TogglTimeEntry(
            workspaceId = 7L,
            projectId = null,
            start = startInstant,
            description = "no project yet",
            duration = -1L,
            createdWith = "Toggl Web",
            id = 1234L,
        )
    every { togglClient.getCurrentTimeEntry() } returns running

    val result = service.getCurrentRunningTimer()

    result.shouldNotBeNull()
    result.startTime shouldBe startInstant
    result.projectName.shouldBeNull()
    result.description shouldBe "no project yet"
    verify(exactly = 0) { togglClient.getProject(any(), any()) }
  }

  @Test
  fun `getCurrentRunningTimer returns null projectName when getProject throws`() {
    val startInstant = Instant.parse("2026-05-15T10:00:00Z")
    val running =
        TogglTimeEntry(
            workspaceId = 7L,
            projectId = 88L,
            start = startInstant,
            description = "going",
            duration = -1L,
            createdWith = "Toggl Web",
            id = 1234L,
        )
    every { togglClient.getCurrentTimeEntry() } returns running
    every { togglClient.getProject(7L, 88L) } throws RuntimeException("toggl down")

    val result = service.getCurrentRunningTimer()

    result.shouldNotBeNull()
    result.startTime shouldBe startInstant
    result.projectName.shouldBeNull()
    result.description shouldBe "going"
  }
}
