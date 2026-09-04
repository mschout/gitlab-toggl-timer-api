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
package io.github.mschout.gitlab.toggltimer.security

import io.github.mschout.gitlab.toggltimer.mfa.MfaService
import io.github.mschout.gitlab.toggltimer.timer.RecentTimeEntryView
import io.github.mschout.gitlab.toggltimer.timer.SplitTimeEntryCommand
import io.github.mschout.gitlab.toggltimer.timer.SplitTimeEntryOutcome
import io.github.mschout.gitlab.toggltimer.timer.StartTimerRequest
import io.github.mschout.gitlab.toggltimer.timer.StartTimerResult
import io.github.mschout.gitlab.toggltimer.timer.StoppedTimerProjectView
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryActionsView
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryDayGroup
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryDeletionService
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryDescriptionEditorView
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryDescriptionService
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryHistoryPage
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryHistoryService
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryNotFoundException
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryProjectPickerView
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryProjectSearchResultView
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryProjectSearchView
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryProjectService
import io.github.mschout.gitlab.toggltimer.timer.TimeEntrySplitView
import io.github.mschout.gitlab.toggltimer.timer.TimeEntrySplitWorkflow
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryStartService
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryStartUpdateOutcome
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryTotalsView
import io.github.mschout.gitlab.toggltimer.timer.TimerService
import io.github.mschout.gitlab.toggltimer.timer.TimerWebController
import io.github.mschout.gitlab.toggltimer.timer.TogglDescriptionUpdateException
import io.github.mschout.gitlab.toggltimer.timer.TogglService
import io.github.mschout.gitlab.toggltimer.timer.TogglTimeEntryDeletionException
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.github.mschout.gitlab.toggltimer.user.User
import io.github.mschout.gitlab.toggltimer.user.UserAuthIdentityRepository
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.github.mschout.gitlab.toggltimer.user.UserSettings
import io.github.mschout.gitlab.toggltimer.user.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Optional
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [TimerWebController::class, SessionKeepAliveController::class])
@Import(SecurityConfig::class, AuthConfiguration::class, SecurityConfigWebMvcTest.MockBeans::class)
class SecurityConfigWebMvcTest(
    @Autowired val mvc: MockMvc,
    @Autowired val timerService: TimerService,
    @Autowired val timeEntryHistoryService: TimeEntryHistoryService,
    @Autowired val timeEntryDescriptionService: TimeEntryDescriptionService,
    @Autowired val timeEntryDeletionService: TimeEntryDeletionService,
    @Autowired val timeEntrySplitWorkflow: TimeEntrySplitWorkflow,
    @Autowired val timeEntryProjectService: TimeEntryProjectService,
    @Autowired val timeEntryStartService: TimeEntryStartService,
    @Autowired val credentialsService: CurrentUserCredentialsService,
) {

  @TestConfiguration
  class MockBeans {
    private val configuredUser = User(email = "alice@example.com", id = 42L)
    private val configuredSettings =
        UserSettings(
            user = configuredUser,
            gitlabAccessToken = "alice-gitlab",
            togglApiKey = "alice-toggl",
            togglWorkspaceId = 7L,
            userId = 42L,
        )

    @Bean fun timerService(): TimerService = mockk(relaxed = true)

    @Bean
    fun togglService(): TogglService =
        mockk<TogglService>(relaxed = true).also {
          every { it.getCurrentRunningTimer() } returns
              StartTimerResult(
                  togglId = 321L,
                  startTime = Instant.parse("2026-08-27T14:30:00Z"),
                  projectName = "74398 - Compact timer",
                  clientName = "Courtio",
                  projectColor = "#4C6EF5",
                  description = "Build the compact toolbar",
              )
        }

    @Bean
    fun timeEntryHistoryService(): TimeEntryHistoryService =
        mockk<TimeEntryHistoryService>(relaxed = true).also {
          every { it.initialPage() } returns
              TimeEntryHistoryPage(
                  groups =
                      listOf(
                          TimeEntryDayGroup(
                              label = "Today",
                              totalFormatted = "0:48:02",
                              entries =
                                  listOf(
                                      RecentTimeEntryView(
                                          descriptionEditor =
                                              TimeEntryDescriptionEditorView(
                                                  togglId = 123L,
                                                  description = "Rendered history entry",
                                              ),
                                          projectPicker =
                                              TimeEntryProjectPickerView(
                                                  togglId = 123L,
                                                  projectName = "74393 - Indiana",
                                                  clientName = "Inforuptcy",
                                                  projectColor = "#4C6EF5",
                                              ),
                                          actions =
                                              TimeEntryActionsView(
                                                  togglId = 123L,
                                                  description = "Rendered history entry",
                                                  split =
                                                      TimeEntrySplitView(
                                                          togglId = 123L,
                                                          expectedStart =
                                                              Instant.parse("2026-08-26T17:36:00Z"),
                                                          expectedStop =
                                                              Instant.parse("2026-08-26T18:24:02Z"),
                                                          durationSeconds = 2_882L,
                                                          splitOffsetSeconds = 1_441L,
                                                          timeZone = "America/Chicago",
                                                          startEpochMilliseconds =
                                                              1_777_225_360_000L,
                                                          startLocalSecondOfDay = 45_360,
                                                          startOffsetSeconds = -18_000,
                                                          stopOffsetSeconds = -18_000,
                                                      ),
                                              ),
                                          timeRange = "12:36 PM – 1:24 PM",
                                          durationFormatted = "0:48:02",
                                      )
                                  ),
                          )
                      ),
                  rangeLabel = "Aug 20–Aug 26, 2026",
                  nextBefore = LocalDate.parse("2026-08-20"),
                  initial = true,
              )
          every { it.currentTotals() } returns
              TimeEntryTotalsView(
                  todayCompletedSeconds = 2_882L,
                  todayCompletedFormatted = "0:48:02",
                  weekCompletedSeconds = 10_923L,
                  weekCompletedFormatted = "3:02:03",
                  todayStart = Instant.parse("2026-08-27T05:00:00Z"),
                  weekStart = Instant.parse("2026-08-24T05:00:00Z"),
                  endExclusive = Instant.parse("2026-08-28T05:00:00Z"),
              )
        }

    @Bean fun timeEntryDescriptionService(): TimeEntryDescriptionService = mockk(relaxed = true)

    @Bean fun timeEntryDeletionService(): TimeEntryDeletionService = mockk(relaxed = true)

    @Bean fun timeEntrySplitWorkflow(): TimeEntrySplitWorkflow = mockk(relaxed = true)

    @Bean fun timeEntryStartService(): TimeEntryStartService = mockk(relaxed = true)

    @Bean
    fun timeEntryProjectService(): TimeEntryProjectService =
        mockk<TimeEntryProjectService>(relaxed = true).also {
          every { it.currentPicker(321L) } returns
              TimeEntryProjectPickerView(
                  togglId = 321L,
                  projectName = "74398 - Compact timer",
                  clientName = "Courtio",
                  projectColor = "#4C6EF5",
              )
        }

    @Bean
    fun currentUserCredentialsService(): CurrentUserCredentialsService =
        mockk<CurrentUserCredentialsService>(relaxed = true).also {
          every { it.currentTimeZone() } returns ZoneId.of("America/Chicago")
        }

    @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-09-03T20:00:00Z"), ZoneOffset.UTC)

    @Bean fun restTemplateBuilder(): RestTemplateBuilder = RestTemplateBuilder()

    @Bean
    fun userRepository(): UserRepository =
        mockk<UserRepository>(relaxed = true).also {
          every { it.findByEmail("alice@example.com") } returns configuredUser
        }

    @Bean fun userAuthIdentityRepository(): UserAuthIdentityRepository = mockk(relaxed = true)

    @Bean
    fun userSettingsRepository(): UserSettingsRepository =
        mockk<UserSettingsRepository>(relaxed = true).also {
          every { it.findById(42L) } returns Optional.of(configuredSettings)
        }

    @Bean
    fun customOidcUserService(): CustomOidcUserService =
        CustomOidcUserService(userRepository(), userAuthIdentityRepository())

    @Bean
    fun customUserDetailsService(): CustomUserDetailsService =
        CustomUserDetailsService(userRepository())

    @Bean
    fun onboardingFilter(): OnboardingFilter =
        OnboardingFilter(userRepository(), userSettingsRepository())

    @Bean fun preMfaGuardFilter(): PreMfaGuardFilter = PreMfaGuardFilter()

    @Bean fun mfaService(): MfaService = mockk(relaxed = true)
  }

  @Test
  fun `unauthenticated GET timer redirects to login`() {
    mvc.perform(get("/timer"))
        .andExpect(status().is3xxRedirection)
        .andExpect(redirectedUrl("/login"))
  }

  @Test
  fun `authenticated GET timer is allowed when user has settings`() {
    mvc.perform(get("/timer").with(user("alice@example.com").roles("USER")))
        .andExpect(status().isOk)
        .andExpect(
            content().string(containsString("/webjars/air-datepicker/3.6.0/air-datepicker.css"))
        )
        .andExpect(
            content().string(containsString("/webjars/air-datepicker/3.6.0/air-datepicker.js"))
        )
        .andExpect(content().string(containsString("/webjars/htmx.org/4.0.0/dist/htmx.min.js")))
        .andExpect(content().string(containsString("htmx:config:request")))
        .andExpect(content().string(containsString("evt.detail.ctx.request.headers[header]")))
        .andExpect(content().string(containsString("htmx:before:request")))
        .andExpect(content().string(containsString("htmx:after:request")))
        .andExpect(content().string(containsString("htmx:after:swap")))
        .andExpect(content().string(containsString("ctx.sourceElement")))
        .andExpect(content().string(containsString("ctx.response.status < 400")))
        .andExpect(content().string(not(containsString("htmx:configRequest"))))
        .andExpect(content().string(not(containsString("htmx:beforeRequest"))))
        .andExpect(content().string(not(containsString("htmx:afterRequest"))))
        .andExpect(content().string(not(containsString("htmx:afterSwap"))))
        .andExpect(content().string(containsString("hx-post=\"/auth/keep-alive\"")))
        .andExpect(content().string(containsString("class=\"running-timer-toolbar shadow-sm\"")))
        .andExpect(content().string(containsString("data-started-at=\"2026-08-27T14:30:00Z\"")))
        .andExpect(content().string(containsString("aria-label=\"Edit timer start time\"")))
        .andExpect(content().string(containsString("aria-haspopup=\"dialog\"")))
        .andExpect(
            content().string(containsString("aria-controls=\"running-timer-start-dialog-321\""))
        )
        .andExpect(content().string(containsString("id=\"running-timer-start-dialog-321\"")))
        .andExpect(content().string(containsString("hx-post=\"/timer/entries/321/start\"")))
        .andExpect(content().string(containsString("name=\"expectedStart\"")))
        .andExpect(content().string(containsString("name=\"startDate\"")))
        .andExpect(content().string(containsString("name=\"startTime\"")))
        .andExpect(content().string(containsString("value=\"9:30 AM\"")))
        .andExpect(content().string(containsString("data-today=\"2026-09-03\"")))
        .andExpect(content().string(containsString(">Cancel</button>")))
        .andExpect(content().string(containsString("running-timer-start-confirm")))
        .andExpect(content().string(containsString("running-timer-start-confirm-label")))
        .andExpect(content().string(containsString("showOtherMonths: false")))
        .andExpect(content().string(containsString("fixedHeight: true")))
        .andExpect(content().string(containsString("firstDay: 1")))
        .andExpect(content().string(containsString("<title>Home • Gitlab Toggl Timer</title>")))
        .andExpect(content().string(containsString("function formatElapsedTitle(seconds)")))
        .andExpect(content().string(containsString("value=\"Build the compact toolbar\"")))
        .andExpect(content().string(containsString("hx-post=\"/timer/entries/321/description\"")))
        .andExpect(
            content().string(containsString("aria-controls=\"time-entry-project-dialog-321\""))
        )
        .andExpect(content().string(containsString("hx-get=\"/timer/entries/321/projects\"")))
        .andExpect(content().string(containsString("74398 - Compact timer")))
        .andExpect(content().string(containsString("Courtio")))
        .andExpect(content().string(containsString("hx-post=\"/timer/stop\"")))
        .andExpect(content().string(containsString("aria-label=\"Stop timer\"")))
        .andExpect(content().string(containsString("id=\"timer-notifications\"")))
        .andExpect(content().string(containsString("aria-live=\"polite\"")))
        .andExpect(content().string(containsString("hx-target=\"#timer-notifications\"")))
        .andExpect(content().string(containsString("bootstrap.Alert.getOrCreateInstance")))
        .andExpect(content().string(containsString("issueUrlConsumed")))
        .andExpect(content().string(containsString("function clearIssueUrl()")))
        .andExpect(content().string(containsString("aria-label=\"Tracked time totals\"")))
        .andExpect(content().string(containsString(">Today</dt>")))
        .andExpect(content().string(containsString(">Week total</dt>")))
        .andExpect(content().string(containsString("data-base-seconds=\"2882\"")))
        .andExpect(content().string(containsString("data-base-seconds=\"10923\"")))
        .andExpect(content().string(containsString("function renderTimeTotals()")))
        .andExpect(content().string(not(containsString("hx-include=\"#running-description\""))))
        .andExpect(
            content()
                .string(containsString("hx-trigger=\"timeEntriesChanged from:body, every 1m\""))
        )
        .andExpect(content().string(containsString("Rendered history entry")))
        .andExpect(content().string(containsString("hx-post=\"/timer/entries/123/description\"")))
        .andExpect(content().string(containsString("placeholder=\"No description\"")))
        .andExpect(content().string(containsString("time-entry-description-saving-123")))
        .andExpect(content().string(containsString("74393 - Indiana")))
        .andExpect(
            content().string(containsString("aria-controls=\"time-entry-project-dialog-123\""))
        )
        .andExpect(content().string(containsString("hx-get=\"/timer/entries/123/projects\"")))
        .andExpect(content().string(containsString("0:48:02")))
        .andExpect(
            content().string(containsString("aria-label=\"Actions for Rendered history entry\""))
        )
        .andExpect(content().string(containsString("data-description=\"Rendered history entry\"")))
        .andExpect(content().string(containsString(">Split</span>")))
        .andExpect(content().string(containsString("time-entry-split-confirm-label")))
        .andExpect(content().string(containsString("hx-post=\"/timer/entries/123/split\"")))
        .andExpect(content().string(containsString("name=\"splitOffsetSeconds\"")))
        .andExpect(content().string(containsString("data-time-zone=\"America/Chicago\"")))
        .andExpect(content().string(containsString(">Enter a time</summary>")))
        .andExpect(content().string(containsString(">Elapsed</label>")))
        .andExpect(content().string(containsString(">Clock time</label>")))
        .andExpect(content().string(containsString("hx-delete=\"/timer/entries/123\"")))
        .andExpect(content().string(containsString("Delete time entry?")))
  }

  @Test
  fun `authenticated HTMX create project is allowed with CSRF header`() {
    every { timerService.createProject(any()) } returns
        TogglProject(id = 100L, name = "42 - Some issue", clientId = 5L)

    mvc.perform(
            post("/timer/create-project")
                .with(user("alice@example.com").roles("USER"))
                .with(csrf().asHeader())
                .header("HX-Request", "true")
                .param("issueUrl", "https://gitlab.com/g/p/-/work_items/42")
                .param("workspaceId", "7")
                .param("clientId", "5")
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("Project ready")))
        .andExpect(content().string(containsString("42 - Some issue")))
        .andExpect(content().string(containsString("alert-success")))
        .andExpect(content().string(containsString("alert-dismissible")))
        .andExpect(content().string(containsString("data-auto-dismiss-after=\"5000\"")))
        .andExpect(content().string(containsString("data-bs-dismiss=\"alert\"")))
  }

  @Test
  fun `authenticated HTMX start renders the reusable running timer toolbar`() {
    every { timerService.startTimer(any()) } returns
        StartTimerResult(
            togglId = 654L,
            startTime = Instant.parse("2026-08-27T15:00:00Z"),
            projectName = null,
            description = "Unassigned work",
        )
    every { timeEntryProjectService.currentPicker(654L) } returns
        TimeEntryProjectPickerView(
            togglId = 654L,
            projectName = null,
            clientName = null,
            projectColor = null,
        )

    mvc.perform(
            post("/timer/start")
                .with(user("alice@example.com").roles("USER"))
                .with(csrf())
                .header("HX-Request", "true")
                .param("workspaceId", "7")
                .param("projectId", "200")
                .param("description", "Unassigned work")
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("running-timer-toolbar")))
        .andExpect(content().string(containsString("hx-post=\"/timer/entries/654/description\"")))
        .andExpect(
            content().string(containsString("aria-controls=\"time-entry-project-dialog-654\""))
        )
        .andExpect(content().string(containsString("data-started-at=\"2026-08-27T15:00:00Z\"")))
        .andExpect(content().string(containsString("hx-post=\"/timer/stop\"")))

    verify {
      timerService.startTimer(
          StartTimerRequest(workspaceId = 7L, projectId = 200L, description = "Unassigned work")
      )
    }
  }

  @Test
  fun `authenticated start-time update replaces the timer toolbar with CSRF`() {
    val updatedStart = Instant.parse("2026-08-27T14:15:00Z")
    every { timeEntryStartService.updateStart(any()) } returns
        TimeEntryStartUpdateOutcome.Saved(
            entry =
                TogglTimeEntry(
                    id = 321L,
                    workspaceId = 7L,
                    start = updatedStart,
                    duration = -1L,
                    description = "Build the compact toolbar",
                ),
            historySynchronized = true,
        )

    mvc.perform(
            post("/timer/entries/321/start")
                .with(user("alice@example.com").roles("USER"))
                .with(csrf())
                .header("HX-Request", "true")
                .param("expectedStart", "2026-08-27T14:30:00Z")
                .param("startDate", "2026-08-27")
                .param("startTime", "9:15 AM")
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("id=\"result\"")))
        .andExpect(content().string(containsString("data-started-at=\"$updatedStart\"")))
        .andExpect(
            content().string(containsString("hx-swap-oob=\"innerHTML:#timer-notifications\""))
        )
  }

  @Test
  fun `start-time update requires CSRF`() {
    mvc.perform(
            post("/timer/entries/321/start")
                .with(user("alice@example.com").roles("USER"))
                .param("expectedStart", "2026-08-27T14:30:00Z")
                .param("startDate", "2026-08-27")
                .param("startTime", "9:15 AM")
        )
        .andExpect(status().isForbidden)
  }

  @Test
  fun `unauthenticated start-time update redirects to login`() {
    mvc.perform(
            post("/timer/entries/321/start")
                .with(csrf())
                .param("expectedStart", "2026-08-27T14:30:00Z")
                .param("startDate", "2026-08-27")
                .param("startTime", "9:15 AM")
        )
        .andExpect(status().is3xxRedirection)
        .andExpect(redirectedUrl("/login"))
  }

  @Test
  fun `authenticated stop renders a clickable stopped timer with project choices`() {
    every { credentialsService.currentTogglWorkspaceId() } returns 7L
    every { timeEntryProjectService.projectsForWorkspace(7L) } returns
        listOf(
            StoppedTimerProjectView(
                togglId = 200L,
                name = "74398 - Compact timer",
                clientName = "Courtio",
                color = "#4C6EF5",
            )
        )

    mvc.perform(
            post("/timer/stop")
                .with(user("alice@example.com").roles("USER"))
                .with(csrf())
                .header("HX-Request", "true")
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("aria-label=\"Stopped timer\"")))
        .andExpect(content().string(containsString("placeholder=\"What are you working on?\"")))
        .andExpect(content().string(containsString("name=\"workspaceId\"")))
        .andExpect(content().string(containsString("value=\"7\"")))
        .andExpect(content().string(containsString("name=\"projectId\"")))
        .andExpect(content().string(containsString("value=\"200\"")))
        .andExpect(content().string(containsString("74398 - Compact timer • Courtio")))
        .andExpect(content().string(containsString("data-project-color=\"#4C6EF5\"")))
        .andExpect(content().string(containsString(">00:00:00</time>")))
        .andExpect(content().string(containsString("hx-post=\"/timer/start\"")))
        .andExpect(content().string(containsString("aria-label=\"Start timer\"")))
        .andExpect(content().string(not(containsString("aria-label=\"Start timer\" disabled"))))
  }

  @Test
  fun `authenticated GET start renders the toolbar with shared timer interactions`() {
    every { timerService.startTimer(any()) } returns
        StartTimerResult(
            togglId = 987L,
            startTime = Instant.parse("2026-08-27T16:00:00Z"),
            projectName = "74398 - Compact timer",
            description = "Direct start",
        )
    every { timeEntryProjectService.currentPicker(987L) } returns
        TimeEntryProjectPickerView(
            togglId = 987L,
            projectName = "74398 - Compact timer",
            clientName = "Courtio",
            projectColor = "#4C6EF5",
        )

    mvc.perform(
            get("/timer/start")
                .with(user("alice@example.com").roles("USER"))
                .param("issueUrl", "https://gitlab.com/g/p/-/work_items/74398")
                .param("workspaceId", "7")
                .param("clientId", "5")
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("running-timer-toolbar")))
        .andExpect(content().string(containsString("hx-post=\"/timer/entries/987/description\"")))
        .andExpect(content().string(containsString("function startElapsedTimers()")))
        .andExpect(content().string(containsString("Back")))
  }

  @Test
  fun `authenticated POST description returns a successful editor fragment`() {
    every { timeEntryDescriptionService.updateDescription(123L, "Updated") } returns
        TimeEntryDescriptionEditorView(togglId = 123L, description = "Updated")

    mvc.perform(
            post("/timer/entries/123/description")
                .with(user("alice@example.com").roles("USER"))
                .with(csrf())
                .param("description", "Updated")
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("value=\"Updated\"")))
        .andExpect(content().string(not(containsString("time-entry-description-error"))))
  }

  @Test
  fun `authenticated POST description renders retry state on failure`() {
    every { timeEntryDescriptionService.updateDescription(123L, "Still typed") } throws
        TogglDescriptionUpdateException(RuntimeException("down"))

    mvc.perform(
            post("/timer/entries/123/description")
                .with(user("alice@example.com").roles("USER"))
                .with(csrf())
                .param("description", "Still typed")
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("value=\"Still typed\"")))
        .andExpect(content().string(containsString("data-editing=\"true\"")))
        .andExpect(content().string(containsString("Could not save to Toggl")))
  }

  @Test
  fun `authenticated POST description returns not found for inaccessible entry`() {
    every { timeEntryDescriptionService.updateDescription(999L, any()) } throws
        TimeEntryNotFoundException(999L)

    mvc.perform(
            post("/timer/entries/999/description")
                .with(user("alice@example.com").roles("USER"))
                .with(csrf())
                .param("description", "No access")
        )
        .andExpect(status().isNotFound)
  }

  @Test
  fun `unauthenticated POST description redirects to login`() {
    mvc.perform(
            post("/timer/entries/123/description").with(csrf()).param("description", "No session")
        )
        .andExpect(status().is3xxRedirection)
        .andExpect(redirectedUrl("/login"))
  }

  @Test
  fun `authenticated DELETE entry is allowed with CSRF`() {
    mvc.perform(
            delete("/timer/entries/123").with(user("alice@example.com").roles("USER")).with(csrf())
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("Recent time entries")))

    verify(exactly = 1) { timeEntryDeletionService.delete(123L) }
  }

  @Test
  fun `authenticated POST split is allowed with CSRF`() {
    val start = Instant.parse("2026-08-26T17:36:00Z")
    val stop = Instant.parse("2026-08-26T18:24:02Z")
    every { timeEntrySplitWorkflow.split(SplitTimeEntryCommand(123L, start, stop, 1_441L)) } returns
        SplitTimeEntryOutcome.Completed

    mvc.perform(
            post("/timer/entries/123/split")
                .with(user("alice@example.com").roles("USER"))
                .with(csrf())
                .param("expectedStart", start.toString())
                .param("expectedStop", stop.toString())
                .param("splitOffsetSeconds", "1441")
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("Recent time entries")))

    verify(exactly = 1) {
      timeEntrySplitWorkflow.split(SplitTimeEntryCommand(123L, start, stop, 1_441L))
    }
  }

  @Test
  fun `POST split requires CSRF`() {
    mvc.perform(
            post("/timer/entries/123/split")
                .with(user("alice@example.com").roles("USER"))
                .param("expectedStart", "2026-08-26T17:36:00Z")
                .param("expectedStop", "2026-08-26T18:24:02Z")
                .param("splitOffsetSeconds", "1441")
        )
        .andExpect(status().isForbidden)
  }

  @Test
  fun `authenticated DELETE entry renders a retryable Toggl failure`() {
    every { timeEntryDeletionService.delete(123L) } throws
        TogglTimeEntryDeletionException(123L, "Rendered history entry", RuntimeException("down"))

    mvc.perform(
            delete("/timer/entries/123").with(user("alice@example.com").roles("USER")).with(csrf())
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("data-open=\"true\"")))
        .andExpect(content().string(containsString("Could not delete from Toggl. Try again.")))
        .andExpect(content().string(containsString("hx-delete=\"/timer/entries/123\"")))
  }

  @Test
  fun `authenticated DELETE entry returns not found for inaccessible entry`() {
    every { timeEntryDeletionService.delete(999L) } throws TimeEntryNotFoundException(999L)

    mvc.perform(
            delete("/timer/entries/999").with(user("alice@example.com").roles("USER")).with(csrf())
        )
        .andExpect(status().isNotFound)
  }

  @Test
  fun `DELETE entry requires CSRF`() {
    mvc.perform(delete("/timer/entries/123").with(user("alice@example.com").roles("USER")))
        .andExpect(status().isForbidden)
  }

  @Test
  fun `unauthenticated DELETE entry redirects to login`() {
    mvc.perform(delete("/timer/entries/123").with(csrf()))
        .andExpect(status().is3xxRedirection)
        .andExpect(redirectedUrl("/login"))
  }

  @Test
  fun `authenticated GET project search renders Postgres results`() {
    every { timeEntryProjectService.searchProjects(123L, "Indiana") } returns
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

    mvc.perform(
            get("/timer/entries/123/projects")
                .with(user("alice@example.com").roles("USER"))
                .param("query", "Indiana")
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("74393 - Indiana")))
        .andExpect(content().string(containsString("Inforuptcy")))
        .andExpect(
            content().string(containsString("hx-post=\"/timer/entries/123/project?projectId=200\""))
        )
  }

  @Test
  fun `authenticated POST project returns the updated picker`() {
    every { timeEntryProjectService.updateProject(123L, 200L) } returns
        TimeEntryProjectPickerView(
            togglId = 123L,
            projectName = "74393 - Indiana",
            clientName = "Inforuptcy",
            projectColor = "#4C6EF5",
        )

    mvc.perform(
            post("/timer/entries/123/project")
                .with(user("alice@example.com").roles("USER"))
                .with(csrf())
                .param("projectId", "200")
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("74393 - Indiana")))
        .andExpect(content().string(containsString("Inforuptcy")))
        .andExpect(content().string(containsString("data-project-color=\"#4C6EF5\"")))
  }

  @Test
  fun `unauthenticated project search redirects to login`() {
    mvc.perform(get("/timer/entries/123/projects").param("query", "Indiana"))
        .andExpect(status().is3xxRedirection)
        .andExpect(redirectedUrl("/login"))
  }

  @Test
  fun `project update requires CSRF`() {
    mvc.perform(
            post("/timer/entries/123/project")
                .with(user("alice@example.com").roles("USER"))
                .param("projectId", "200")
        )
        .andExpect(status().isForbidden)
  }

  @Test
  fun `load more rows render the same description editor`() {
    every { timeEntryHistoryService.pageBefore(LocalDate.parse("2026-08-20")) } returns
        timeEntryHistoryService.initialPage().copy(initial = false)
    mvc.perform(
            get("/timer/entries/page")
                .with(user("alice@example.com").roles("USER"))
                .param("before", "2026-08-20")
        )
        .andExpect(status().isOk)
        .andExpect(content().string(containsString("hx-post=\"/timer/entries/123/description\"")))
  }

  @Test
  fun `unauthenticated GET root is permitted`() {
    mvc.perform(get("/"))
        .andExpect(status().isOk)
        .andExpect(content().string(not(containsString("hx-post=\"/auth/keep-alive\""))))
  }

  @Test
  fun `authenticated POST keep-alive refreshes the session`() {
    mvc.perform(post("/auth/keep-alive").with(user("alice@example.com").roles("USER")).with(csrf()))
        .andExpect(status().isNoContent)
  }

  @Test
  fun `unauthenticated POST keep-alive redirects to login`() {
    mvc.perform(post("/auth/keep-alive").with(csrf()))
        .andExpect(status().is3xxRedirection)
        .andExpect(redirectedUrl("/login"))
  }
}
