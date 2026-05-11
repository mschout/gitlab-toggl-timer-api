package io.github.mschout.gitlab.toggltimer.security

import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class AuthAdvice(private val authProperties: AuthProperties) {

  @ModelAttribute("passwordLoginEnabled")
  fun passwordLoginEnabled(): Boolean = authProperties.passwordLoginEnabled
}
