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
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

private val logger = KotlinLogging.logger {}

@Controller
@RequestMapping("/settings")
class UserSettingsController(
    private val credentialsService: CurrentUserCredentialsService,
    private val userSettingsRepository: UserSettingsRepository,
    private val togglService: TogglService,
) {

  @GetMapping
  fun show(model: Model): String {
    val settings = credentialsService.currentSettings()
    if (!model.containsAttribute("form")) {
      model.addAttribute("form", SettingsForm(togglWorkspaceId = settings?.togglWorkspaceId))
    }
    model.addAttribute("hasGitlabToken", !settings?.gitlabAccessToken.isNullOrBlank())
    model.addAttribute("hasTogglApiKey", !settings?.togglApiKey.isNullOrBlank())
    model.addAttribute("currentWorkspaceId", settings?.togglWorkspaceId)

    val apiKey = settings?.togglApiKey?.takeIf { it.isNotBlank() }
    if (apiKey != null && !model.containsAttribute("workspaces")) {
      val (workspaces, error) = loadWorkspaces(apiKey)
      model.addAttribute("workspaces", workspaces)
      if (error != null) model.addAttribute("workspacesError", error)
    }
    return "settings"
  }

  @PostMapping
  @Transactional
  fun save(@ModelAttribute("form") form: SettingsForm, redirectAttrs: RedirectAttributes): String {
    val user = credentialsService.currentUser()
    val settings = userSettingsRepository.findById(user.id).orElseGet { UserSettings(user = user) }

    form.gitlabAccessToken?.takeIf { it.isNotBlank() }?.let { settings.gitlabAccessToken = it }
    form.togglApiKey?.takeIf { it.isNotBlank() }?.let { settings.togglApiKey = it }
    form.togglWorkspaceId?.let { settings.togglWorkspaceId = it }

    userSettingsRepository.save(settings)

    val apiKey = settings.togglApiKey?.takeIf { it.isNotBlank() }
    if (settings.togglWorkspaceId == null && apiKey != null) {
      val (workspaces, error) = loadWorkspaces(apiKey)
      when {
        error != null -> {
          redirectAttrs.addFlashAttribute("workspacesError", error)
          return "redirect:/settings"
        }
        workspaces.isEmpty() -> {
          redirectAttrs.addFlashAttribute(
              "workspacesError",
              "No Toggl workspaces were found for this account.",
          )
          return "redirect:/settings"
        }
        workspaces.size == 1 -> {
          settings.togglWorkspaceId = workspaces.single().id
          userSettingsRepository.save(settings)
        }
        else -> {
          redirectAttrs.addFlashAttribute("workspaces", workspaces)
          redirectAttrs.addFlashAttribute("workspacesPrompt", "Choose a default Toggl workspace.")
          return "redirect:/settings"
        }
      }
    }

    return "redirect:/timer"
  }

  private fun loadWorkspaces(apiKey: String): Pair<List<TogglWorkspace>, String?> =
      try {
        togglService.fetchWorkspaces(apiKey) to null
      } catch (ex: Exception) {
        logger.warn(ex) { "Failed to fetch Toggl workspaces" }
        emptyList<TogglWorkspace>() to
            "Could not load Toggl workspaces — check that the API key is valid."
      }
}
