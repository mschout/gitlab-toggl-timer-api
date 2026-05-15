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

import io.github.mschout.gitlab.toggltimer.security.AuthProperties
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.support.RedirectAttributes

const val MIN_PASSWORD_LENGTH = 12

@Controller
@RequestMapping("/settings/sign-in")
class SignInSettingsController(
    private val credentialsService: CurrentUserCredentialsService,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authProperties: AuthProperties,
) {

  @GetMapping
  fun show(model: Model): String {
    requirePasswordLoginEnabled()
    val user = credentialsService.currentUser()
    if (!model.containsAttribute("form")) {
      model.addAttribute("form", PasswordChangeForm())
    }
    model.addAttribute("hasPassword", user.passwordHash != null)
    return "settings/sign-in"
  }

  @PostMapping
  @Transactional
  fun save(
      @ModelAttribute("form") form: PasswordChangeForm,
      redirectAttrs: RedirectAttributes,
  ): String {
    requirePasswordLoginEnabled()
    val user = credentialsService.currentUser()
    val hasPassword = user.passwordHash != null

    val error = validate(form, user, hasPassword)
    if (error != null) {
      redirectAttrs.addFlashAttribute("error", error)
      return "redirect:/settings/sign-in"
    }

    user.passwordHash = passwordEncoder.encode(form.newPassword)
    userRepository.save(user)

    redirectAttrs.addFlashAttribute(
        "success",
        if (hasPassword) "Password updated."
        else "Password set. You can now sign in with email and password.",
    )
    return "redirect:/settings/sign-in"
  }

  private fun validate(form: PasswordChangeForm, user: User, hasPassword: Boolean): String? {
    if (hasPassword) {
      val current = form.currentPassword.orEmpty()
      if (current.isBlank() || !passwordEncoder.matches(current, user.passwordHash)) {
        return "Current password is incorrect."
      }
    }
    if (form.newPassword.length < MIN_PASSWORD_LENGTH) {
      return "Password must be at least $MIN_PASSWORD_LENGTH characters."
    }
    if (form.newPassword != form.confirmPassword) {
      return "Passwords do not match."
    }
    return null
  }

  private fun requirePasswordLoginEnabled() {
    if (!authProperties.passwordLoginEnabled) {
      throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }
  }
}
