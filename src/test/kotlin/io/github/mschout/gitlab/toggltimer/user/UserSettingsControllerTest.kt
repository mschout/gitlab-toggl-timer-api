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
package io.github.mschout.gitlab.toggltimer.user

import io.github.mschout.gitlab.toggltimer.timer.TogglService
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional
import org.junit.jupiter.api.Test
import org.springframework.ui.ExtendedModelMap
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap

class UserSettingsControllerTest {

  private val credentialsService = mockk<CurrentUserCredentialsService>()
  private val settingsRepository = mockk<UserSettingsRepository>()
  private val togglService = mockk<TogglService>()
  private val controller =
      UserSettingsController(credentialsService, settingsRepository, togglService)

  @Test
  fun `show renders settings view with empty form when no settings`() {
    every { credentialsService.currentSettings() } returns null
    val model = ExtendedModelMap()

    val view = controller.show(model)

    view shouldBe "settings"
    model["form"] shouldBe SettingsForm()
    model["hasGitlabToken"] shouldBe false
    model["hasTogglApiKey"] shouldBe false
    model["currentWorkspaceId"] shouldBe null
    (model["timeZones"] as List<*>).contains(DEFAULT_TIME_ZONE_ID) shouldBe true
  }

  @Test
  fun `show flags credentials present when both saved and loads workspaces`() {
    val user = User(email = "alice@example.com", id = 1L)
    every { credentialsService.currentSettings() } returns
        UserSettings(
            user = user,
            gitlabAccessToken = "glpat",
            togglApiKey = "tog",
            togglWorkspaceId = 42L,
            userId = 1L,
        )
    val workspaces = listOf(TogglWorkspace(id = 42L, name = "Acme"))
    every { togglService.fetchWorkspaces("tog") } returns workspaces
    val model = ExtendedModelMap()

    controller.show(model)

    model["hasGitlabToken"] shouldBe true
    model["hasTogglApiKey"] shouldBe true
    model["currentWorkspaceId"] shouldBe 42L
    model["workspaces"] shouldBe workspaces
    model.containsAttribute("workspacesError") shouldBe false
    (model["form"] as SettingsForm).togglWorkspaceId shouldBe 42L
  }

  @Test
  fun `show surfaces workspaces error when toggl fetch fails`() {
    val user = User(email = "alice@example.com", id = 1L)
    every { credentialsService.currentSettings() } returns
        UserSettings(user = user, togglApiKey = "tog", userId = 1L)
    every { togglService.fetchWorkspaces("tog") } throws RuntimeException("boom")
    val model = ExtendedModelMap()

    controller.show(model)

    model["workspaces"] shouldBe emptyList<TogglWorkspace>()
    model["workspacesError"] shouldBe
        "Could not load Toggl workspaces — check that the API key is valid."
  }

  @Test
  fun `save creates new settings persists credentials and auto-selects single workspace`() {
    val user = User(email = "alice@example.com", id = 1L)
    every { credentialsService.currentUser() } returns user
    every { settingsRepository.findById(1L) } returns Optional.empty()
    val saved = slot<UserSettings>()
    every { settingsRepository.save(capture(saved)) } answers { saved.captured }
    every { togglService.fetchWorkspaces("toggl-key") } returns
        listOf(TogglWorkspace(id = 99L, name = "Only Workspace"))

    val view =
        controller.save(
            SettingsForm(gitlabAccessToken = "glpat", togglApiKey = "toggl-key"),
            RedirectAttributesModelMap(),
        )

    view shouldBe "redirect:/timer"
    saved.captured.gitlabAccessToken shouldBe "glpat"
    saved.captured.togglApiKey shouldBe "toggl-key"
    saved.captured.togglWorkspaceId shouldBe 99L
    verify(exactly = 2) { settingsRepository.save(any()) }
  }

  @Test
  fun `save redirects back to settings with prompt when multiple workspaces`() {
    val user = User(email = "alice@example.com", id = 1L)
    every { credentialsService.currentUser() } returns user
    every { settingsRepository.findById(1L) } returns Optional.empty()
    val saved = slot<UserSettings>()
    every { settingsRepository.save(capture(saved)) } answers { saved.captured }
    val workspaces =
        listOf(TogglWorkspace(id = 1L, name = "Alpha"), TogglWorkspace(id = 2L, name = "Beta"))
    every { togglService.fetchWorkspaces("toggl-key") } returns workspaces
    val redirectAttrs = RedirectAttributesModelMap()

    val view =
        controller.save(
            SettingsForm(gitlabAccessToken = "glpat", togglApiKey = "toggl-key"),
            redirectAttrs,
        )

    view shouldBe "redirect:/settings"
    saved.captured.togglWorkspaceId shouldBe null
    redirectAttrs.flashAttributes["workspaces"] shouldBe workspaces
    (redirectAttrs.flashAttributes["workspacesPrompt"] as String).shouldBe(
        "Choose a default Toggl workspace."
    )
  }

  @Test
  fun `save redirects to settings with error when workspace fetch throws`() {
    val user = User(email = "alice@example.com", id = 1L)
    every { credentialsService.currentUser() } returns user
    every { settingsRepository.findById(1L) } returns Optional.empty()
    every { settingsRepository.save(any()) } answers { firstArg() }
    every { togglService.fetchWorkspaces("toggl-key") } throws RuntimeException("nope")
    val redirectAttrs = RedirectAttributesModelMap()

    val view =
        controller.save(
            SettingsForm(gitlabAccessToken = "glpat", togglApiKey = "toggl-key"),
            redirectAttrs,
        )

    view shouldBe "redirect:/settings"
    redirectAttrs.flashAttributes["workspacesError"] shouldBe
        "Could not load Toggl workspaces — check that the API key is valid."
  }

  @Test
  fun `save redirects to settings with error when zero workspaces returned`() {
    val user = User(email = "alice@example.com", id = 1L)
    every { credentialsService.currentUser() } returns user
    every { settingsRepository.findById(1L) } returns Optional.empty()
    every { settingsRepository.save(any()) } answers { firstArg() }
    every { togglService.fetchWorkspaces("toggl-key") } returns emptyList()
    val redirectAttrs = RedirectAttributesModelMap()

    val view =
        controller.save(
            SettingsForm(gitlabAccessToken = "glpat", togglApiKey = "toggl-key"),
            redirectAttrs,
        )

    view shouldBe "redirect:/settings"
    redirectAttrs.flashAttributes["workspacesError"] shouldBe
        "No Toggl workspaces were found for this account."
  }

  @Test
  fun `save accepts explicit workspace selection without re-querying toggl`() {
    val user = User(email = "alice@example.com", id = 1L)
    val existing =
        UserSettings(
            user = user,
            gitlabAccessToken = "glpat",
            togglApiKey = "toggl-key",
            togglWorkspaceId = null,
            userId = 1L,
        )
    every { credentialsService.currentUser() } returns user
    every { settingsRepository.findById(1L) } returns Optional.of(existing)
    every { settingsRepository.save(existing) } returns existing

    val view =
        controller.save(
            SettingsForm(togglWorkspaceId = 7L, timeZone = "America/Denver"),
            RedirectAttributesModelMap(),
        )

    view shouldBe "redirect:/timer"
    existing.togglWorkspaceId shouldBe 7L
    existing.timeZone shouldBe "America/Denver"
    verify(exactly = 0) { togglService.fetchWorkspaces(any()) }
  }

  @Test
  fun `save rejects an invalid time zone before changing settings`() {
    val redirectAttrs = RedirectAttributesModelMap()
    val form = SettingsForm(timeZone = "Central-ish")

    val view = controller.save(form, redirectAttrs)

    view shouldBe "redirect:/settings"
    redirectAttrs.flashAttributes["form"] shouldBe form
    redirectAttrs.flashAttributes["timeZoneError"] shouldBe "Choose a valid time zone."
    verify(exactly = 0) { credentialsService.currentUser() }
    verify(exactly = 0) { settingsRepository.save(any()) }
  }

  @Test
  fun `save preserves existing credential when form field is blank`() {
    val user = User(email = "alice@example.com", id = 1L)
    val existing =
        UserSettings(
            user = user,
            gitlabAccessToken = "previous-gitlab",
            togglApiKey = "previous-toggl",
            togglWorkspaceId = 5L,
            userId = 1L,
        )
    every { credentialsService.currentUser() } returns user
    every { settingsRepository.findById(1L) } returns Optional.of(existing)
    every { settingsRepository.save(existing) } returns existing

    controller.save(
        SettingsForm(gitlabAccessToken = "new-gitlab", togglApiKey = ""),
        RedirectAttributesModelMap(),
    )

    existing.gitlabAccessToken shouldBe "new-gitlab"
    existing.togglApiKey shouldBe "previous-toggl"
    existing.togglWorkspaceId shouldBe 5L
    verify { settingsRepository.save(existing) }
    verify(exactly = 0) { togglService.fetchWorkspaces(any()) }
  }
}
