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
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryDayGroup
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryDescriptionEditorView
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryDescriptionService
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryHistoryPage
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryHistoryService
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryNotFoundException
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryProjectPickerView
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryProjectSearchResultView
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryProjectSearchView
import io.github.mschout.gitlab.toggltimer.timer.TimeEntryProjectService
import io.github.mschout.gitlab.toggltimer.timer.TimerService
import io.github.mschout.gitlab.toggltimer.timer.TimerWebController
import io.github.mschout.gitlab.toggltimer.timer.TogglDescriptionUpdateException
import io.github.mschout.gitlab.toggltimer.timer.TogglService
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.github.mschout.gitlab.toggltimer.user.User
import io.github.mschout.gitlab.toggltimer.user.UserAuthIdentityRepository
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.github.mschout.gitlab.toggltimer.user.UserSettings
import io.github.mschout.gitlab.toggltimer.user.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [TimerWebController::class, SessionKeepAliveController::class])
@Import(SecurityConfig::class, AuthConfiguration::class, SecurityConfigWebMvcTest.MockBeans::class)
class SecurityConfigWebMvcTest(
    @Autowired val mvc: MockMvc,
    @Autowired val timeEntryHistoryService: TimeEntryHistoryService,
    @Autowired val timeEntryDescriptionService: TimeEntryDescriptionService,
    @Autowired val timeEntryProjectService: TimeEntryProjectService,
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

    @Bean fun togglService(): TogglService = mockk(relaxed = true)

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
        }

    @Bean fun timeEntryDescriptionService(): TimeEntryDescriptionService = mockk(relaxed = true)

    @Bean fun timeEntryProjectService(): TimeEntryProjectService = mockk(relaxed = true)

    @Bean fun currentUserCredentialsService(): CurrentUserCredentialsService = mockk(relaxed = true)

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
        .andExpect(content().string(containsString("hx-post=\"/auth/keep-alive\"")))
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
