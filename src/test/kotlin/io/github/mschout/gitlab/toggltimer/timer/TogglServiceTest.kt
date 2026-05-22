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

import io.github.mschout.gitlab.toggltimer.project.TogglSyncService
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
import java.time.LocalDate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TogglServiceTest {

  private lateinit var togglClient: TogglClient
  private lateinit var togglClientFactory: TogglClientFactory
  private lateinit var credentialsService: CurrentUserCredentialsService
  private lateinit var togglSyncService: TogglSyncService
  private lateinit var service: TogglService

  @BeforeEach
  fun setUp() {
    togglClient = mockk()
    togglClientFactory = mockk()
    credentialsService = mockk()
    togglSyncService = mockk(relaxUnitFun = true)
    every { credentialsService.requireTogglApiKey() } returns "test-api-key"
    every { credentialsService.currentUserId() } returns 42L
    every { togglClientFactory.forApiKey("test-api-key") } returns togglClient
    service = TogglService(togglClientFactory, credentialsService, togglSyncService)
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
    verify(exactly = 0) { togglSyncService.upsertWorkspaces(any()) }
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
  fun `fetchWorkspaces (no-arg) shadow-writes workspaces to Postgres via the sync service`() {
    val workspaces =
        listOf(TogglWorkspace(id = 1L, name = "Alpha"), TogglWorkspace(id = 2L, name = "Beta"))
    every { togglClient.getWorkspaces() } returns workspaces

    service.fetchWorkspaces() shouldBe workspaces

    verify { togglSyncService.upsertWorkspaces(workspaces) }
  }

  @Test
  fun `fetchWorkspaces (no-arg) still returns workspaces when the sync service throws`() {
    val workspaces = listOf(TogglWorkspace(id = 1L, name = "Alpha"))
    every { togglClient.getWorkspaces() } returns workspaces
    every { togglSyncService.upsertWorkspaces(workspaces) } throws RuntimeException("db down")

    service.fetchWorkspaces() shouldBe workspaces
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
  fun `fetchClients shadow-writes clients to Postgres via the sync service`() {
    val clients =
        listOf(
            TogglWorkspaceClient(id = 10L, name = "Globex"),
            TogglWorkspaceClient(id = 11L, name = "Initech"),
        )
    every { togglClient.getClients(7L) } returns clients

    service.fetchClients(7L) shouldBe clients

    verify { togglSyncService.upsertClients(7L, clients) }
  }

  @Test
  fun `fetchClients still returns Toggl clients when the sync service throws`() {
    val clients = listOf(TogglWorkspaceClient(id = 10L, name = "Globex"))
    every { togglClient.getClients(7L) } returns clients
    every { togglSyncService.upsertClients(7L, clients) } throws RuntimeException("db down")

    service.fetchClients(7L) shouldBe clients
  }

  @Test
  fun `findOrCreateProject shadow-writes the existing project to Postgres`() {
    val existing = TogglProject(id = 100L, name = "42 - Existing", clientId = 5L)
    every { togglClient.getProjects(7L, "42") } returns listOf(existing)

    service.findOrCreateProject(
        workspaceId = 7L,
        clientId = 5L,
        issueNumber = 42L,
        issueTitle = "Existing",
    )

    verify { togglSyncService.upsertProject(7L, existing) }
  }

  @Test
  fun `findOrCreateProject shadow-writes the created project to Postgres`() {
    every { togglClient.getProjects(7L, "42") } returns emptyList()
    val created = TogglProject(id = 999L, name = "42 - New", clientId = 5L)
    every { togglClient.createProject(7L, any()) } returns created

    service.findOrCreateProject(
        workspaceId = 7L,
        clientId = 5L,
        issueNumber = 42L,
        issueTitle = "New",
    )

    verify { togglSyncService.upsertProject(7L, created) }
  }

  @Test
  fun `findOrCreateProject still returns the project when the sync service throws`() {
    val existing = TogglProject(id = 100L, name = "42 - Existing", clientId = 5L)
    every { togglClient.getProjects(7L, "42") } returns listOf(existing)
    every { togglSyncService.upsertProject(7L, existing) } throws RuntimeException("db down")

    val result =
        service.findOrCreateProject(
            workspaceId = 7L,
            clientId = 5L,
            issueNumber = 42L,
            issueTitle = "Existing",
        )

    result shouldBeSameInstanceAs existing
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
  fun `getCurrentRunningTimer returns null when entry is stopped`() {
    val stopped =
        TogglTimeEntry(
            workspaceId = 7L,
            projectId = 88L,
            start = Instant.parse("2026-05-15T10:00:00Z"),
            description = "already stopped",
            duration = 125L,
            id = 1234L,
        )
    every { togglClient.getCurrentTimeEntry() } returns stopped

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

  @Test
  fun `startTimer shadow-writes the newly created time entry to Postgres`() {
    val project = TogglProject(id = 99L, name = "42 - X", clientId = 5L)
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
            start = Instant.parse("2026-05-22T12:00:00Z"),
        )
    every { togglClient.getCurrentTimeEntry() } returns null
    val created =
        TogglTimeEntry(
            id = 555L,
            workspaceId = 7L,
            projectId = 99L,
            start = request.start,
            duration = -1L,
        )
    every { togglClient.createTimeEntry(7L, any()) } returns created

    service.startTimer(project, request)

    verify { togglSyncService.upsertTimeEntry(42L, created) }
  }

  @Test
  fun `startTimer still returns when shadow-write of created entry throws`() {
    val project = TogglProject(id = 99L, name = "42 - X", clientId = 5L)
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
            start = Instant.parse("2026-05-22T12:00:00Z"),
        )
    every { togglClient.getCurrentTimeEntry() } returns null
    val created =
        TogglTimeEntry(
            id = 555L,
            workspaceId = 7L,
            projectId = 99L,
            start = request.start,
            duration = -1L,
        )
    every { togglClient.createTimeEntry(7L, any()) } returns created
    every { togglSyncService.upsertTimeEntry(42L, created) } throws RuntimeException("db down")

    val result = service.startTimer(project, request)
    result.shouldNotBeNull()
  }

  @Test
  fun `startTimer shadow-writes the updated entry when assigning a project to a running entry`() {
    val project = TogglProject(id = 99L, name = "42 - X", clientId = 5L)
    val request =
        StartTimerRequest(
            issueUrl = "https://gitlab.com/g/p/-/issues/42",
            workspaceId = 7L,
            clientId = 5L,
        )
    val running =
        TogglTimeEntry(
            workspaceId = 7L,
            projectId = null,
            start = Instant.parse("2026-05-22T12:00:00Z"),
            duration = -1L,
            id = 1234L,
        )
    every { togglClient.getCurrentTimeEntry() } returns running
    val updated = running.copy(projectId = 99L)
    every { togglClient.updateTimeEntry(7L, 1234L, any()) } returns updated

    service.startTimer(project, request)

    verify { togglSyncService.upsertTimeEntry(42L, updated) }
  }

  @Test
  fun `stopRunningTimer shadow-writes the stopped entry to Postgres`() {
    val running =
        TogglTimeEntry(
            workspaceId = 7L,
            projectId = 99L,
            start = Instant.parse("2026-05-22T12:00:00Z"),
            duration = -1L,
            id = 1234L,
        )
    every { togglClient.getCurrentTimeEntry() } returns running
    val stopped = running.copy(duration = 125L, stop = Instant.parse("2026-05-22T12:02:05Z"))
    every { togglClient.stopTimeEntry(7L, 1234L) } returns stopped

    service.stopRunningTimer()

    verify { togglSyncService.upsertTimeEntry(42L, stopped) }
  }

  @Test
  fun `stopRunningTimer still returns when shadow-write of stopped entry throws`() {
    val running =
        TogglTimeEntry(
            workspaceId = 7L,
            projectId = 99L,
            start = Instant.parse("2026-05-22T12:00:00Z"),
            duration = -1L,
            id = 1234L,
        )
    every { togglClient.getCurrentTimeEntry() } returns running
    val stopped = running.copy(duration = 125L)
    every { togglClient.stopTimeEntry(7L, 1234L) } returns stopped
    every { togglSyncService.upsertTimeEntry(42L, stopped) } throws RuntimeException("db down")

    val result = service.stopRunningTimer()
    result.shouldNotBeNull()
  }

  @Test
  fun `backfillProjects fetches projects across all workspaces and bulk-upserts them`() {
    val workspaces =
        listOf(TogglWorkspace(id = 7L, name = "Alpha"), TogglWorkspace(id = 8L, name = "Beta"))
    val alphaProjects =
        listOf(
            TogglProject(id = 100L, name = "A1", clientId = 5L),
            TogglProject(id = 101L, name = "A2", clientId = 5L),
        )
    val betaProjects = listOf(TogglProject(id = 200L, name = "B1", clientId = null))
    every { togglClient.getWorkspaces() } returns workspaces
    every { togglClient.getProjects(7L, null) } returns alphaProjects
    every { togglClient.getProjects(8L, null) } returns betaProjects

    val result = service.backfillProjects()

    result.count shouldBe 3
    result.workspaces shouldBe 2
    verify { togglSyncService.upsertWorkspaces(workspaces) }
    verify { togglSyncService.upsertProjects(7L, alphaProjects) }
    verify { togglSyncService.upsertProjects(8L, betaProjects) }
  }

  @Test
  fun `backfillProjects still returns the total when one workspace sync throws`() {
    val workspaces =
        listOf(TogglWorkspace(id = 7L, name = "Alpha"), TogglWorkspace(id = 8L, name = "Beta"))
    val alphaProjects = listOf(TogglProject(id = 100L, name = "A1"))
    val betaProjects = listOf(TogglProject(id = 200L, name = "B1"))
    every { togglClient.getWorkspaces() } returns workspaces
    every { togglClient.getProjects(7L, null) } returns alphaProjects
    every { togglClient.getProjects(8L, null) } returns betaProjects
    every { togglSyncService.upsertProjects(7L, alphaProjects) } throws RuntimeException("db down")

    val result = service.backfillProjects()

    result.count shouldBe 2
    result.workspaces shouldBe 2
    verify { togglSyncService.upsertProjects(8L, betaProjects) }
  }

  @Test
  fun `backfillProjects still proceeds when the workspace sync throws`() {
    val workspaces = listOf(TogglWorkspace(id = 7L, name = "Alpha"))
    val projects = listOf(TogglProject(id = 100L, name = "A1"))
    every { togglClient.getWorkspaces() } returns workspaces
    every { togglClient.getProjects(7L, null) } returns projects
    every { togglSyncService.upsertWorkspaces(workspaces) } throws RuntimeException("db down")

    val result = service.backfillProjects()

    result.count shouldBe 1
    result.workspaces shouldBe 1
    verify { togglSyncService.upsertProjects(7L, projects) }
  }

  @Test
  fun `backfillTimeEntries fetches by date range and bulk-upserts via the sync service`() {
    val start = LocalDate.parse("2026-04-22")
    val end = LocalDate.parse("2026-05-22")
    val entries =
        listOf(
            TogglTimeEntry(id = 1L, workspaceId = 7L, start = Instant.now(), duration = 100L),
            TogglTimeEntry(id = 2L, workspaceId = 7L, start = Instant.now(), duration = 200L),
        )
    every {
      togglClient.getTimeEntries(startDate = "2026-04-22", endDate = "2026-05-22", meta = true)
    } returns entries

    val count = service.backfillTimeEntries(start, end)

    count shouldBe 2
    verify { togglSyncService.upsertTimeEntries(42L, entries) }
  }

  @Test
  fun `backfillTimeEntries still returns the count when the sync service throws`() {
    val start = LocalDate.parse("2026-04-22")
    val end = LocalDate.parse("2026-05-22")
    val entries = listOf(TogglTimeEntry(id = 1L, workspaceId = 7L, start = Instant.now()))
    every {
      togglClient.getTimeEntries(startDate = "2026-04-22", endDate = "2026-05-22", meta = true)
    } returns entries
    every { togglSyncService.upsertTimeEntries(42L, entries) } throws RuntimeException("db down")

    service.backfillTimeEntries(start, end) shouldBe 1
  }
}
