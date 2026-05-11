package io.github.mschout.gitlab.toggltimer.user

import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/settings")
class UserSettingsController(
    private val credentialsService: CurrentUserCredentialsService,
    private val userSettingsRepository: UserSettingsRepository,
) {

  @GetMapping
  fun show(model: Model): String {
    val settings = credentialsService.currentSettings()
    if (!model.containsAttribute("form")) {
      model.addAttribute("form", SettingsForm())
    }
    model.addAttribute("hasGitlabToken", !settings?.gitlabAccessToken.isNullOrBlank())
    model.addAttribute("hasTogglApiKey", !settings?.togglApiKey.isNullOrBlank())
    return "settings"
  }

  @PostMapping
  @Transactional
  fun save(@ModelAttribute("form") form: SettingsForm): String {
    val user = credentialsService.currentUser()
    val settings = userSettingsRepository.findById(user.id).orElseGet { UserSettings(user = user) }

    form.gitlabAccessToken?.takeIf { it.isNotBlank() }?.let { settings.gitlabAccessToken = it }
    form.togglApiKey?.takeIf { it.isNotBlank() }?.let { settings.togglApiKey = it }

    userSettingsRepository.save(settings)
    return "redirect:/timer"
  }
}
