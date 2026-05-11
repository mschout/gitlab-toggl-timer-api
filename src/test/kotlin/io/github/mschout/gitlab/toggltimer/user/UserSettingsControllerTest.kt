package io.github.mschout.gitlab.toggltimer.user

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional
import org.junit.jupiter.api.Test
import org.springframework.ui.ExtendedModelMap

class UserSettingsControllerTest {

  private val credentialsService = mockk<CurrentUserCredentialsService>()
  private val settingsRepository = mockk<UserSettingsRepository>()
  private val controller = UserSettingsController(credentialsService, settingsRepository)

  @Test
  fun `show renders settings view with empty form when no settings`() {
    every { credentialsService.currentSettings() } returns null
    val model = ExtendedModelMap()

    val view = controller.show(model)

    view shouldBe "settings"
    model["form"] shouldBe SettingsForm()
    model["hasGitlabToken"] shouldBe false
    model["hasTogglApiKey"] shouldBe false
  }

  @Test
  fun `show flags credentials present when both saved`() {
    val user = User(email = "alice@example.com", id = 1L)
    every { credentialsService.currentSettings() } returns
        UserSettings(user = user, gitlabAccessToken = "glpat", togglApiKey = "tog", userId = 1L)
    val model = ExtendedModelMap()

    controller.show(model)

    model["hasGitlabToken"] shouldBe true
    model["hasTogglApiKey"] shouldBe true
  }

  @Test
  fun `save creates new settings when none exist and persists submitted credentials`() {
    val user = User(email = "alice@example.com", id = 1L)
    every { credentialsService.currentUser() } returns user
    every { settingsRepository.findById(1L) } returns Optional.empty()
    val saved = slot<UserSettings>()
    every { settingsRepository.save(capture(saved)) } answers { saved.captured }

    val view = controller.save(SettingsForm(gitlabAccessToken = "glpat", togglApiKey = "toggl-key"))

    view shouldBe "redirect:/timer"
    saved.captured.gitlabAccessToken shouldBe "glpat"
    saved.captured.togglApiKey shouldBe "toggl-key"
    saved.captured.user shouldBe user
  }

  @Test
  fun `save preserves existing credential when form field is blank`() {
    val user = User(email = "alice@example.com", id = 1L)
    val existing =
        UserSettings(
            user = user,
            gitlabAccessToken = "previous-gitlab",
            togglApiKey = "previous-toggl",
            userId = 1L,
        )
    every { credentialsService.currentUser() } returns user
    every { settingsRepository.findById(1L) } returns Optional.of(existing)
    every { settingsRepository.save(existing) } returns existing

    controller.save(SettingsForm(gitlabAccessToken = "new-gitlab", togglApiKey = ""))

    existing.gitlabAccessToken shouldBe "new-gitlab"
    existing.togglApiKey shouldBe "previous-toggl"
    verify { settingsRepository.save(existing) }
  }
}
