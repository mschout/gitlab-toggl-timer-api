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
package io.github.mschout.gitlab.toggltimer.mfa

import io.github.mschout.gitlab.toggltimer.security.CustomUserDetailsService
import io.github.mschout.gitlab.toggltimer.security.PreMfaAuthenticationToken
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/login/mfa")
class MfaChallengeController(
    private val mfaService: MfaService,
    private val userRepository: UserRepository,
    private val userDetailsService: CustomUserDetailsService,
) {

  private val securityContextRepository: SecurityContextRepository =
      HttpSessionSecurityContextRepository()

  @GetMapping
  fun show(): String {
    requirePreMfaPrincipal()
    return "login/mfa"
  }

  @PostMapping
  fun submit(
      @RequestParam("code", required = false) code: String?,
      @RequestParam(name = "type", defaultValue = "totp") type: String,
      request: HttpServletRequest,
      response: HttpServletResponse,
      redirectAttrs: RedirectAttributes,
  ): String {
    val auth = requirePreMfaPrincipal()
    val email = auth.name
    val user =
        userRepository.findByEmail(email) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    val input = code?.trim().orEmpty()
    if (input.isEmpty()) {
      redirectAttrs.addFlashAttribute("error", "Enter a code.")
      return "redirect:/login/mfa"
    }
    val ok =
        when (type) {
          "recovery" -> mfaService.verifyAndConsumeRecoveryCode(user, input)
          else -> mfaService.verifyTotpChallenge(user, input)
        }
    if (!ok) {
      redirectAttrs.addFlashAttribute("error", "Invalid code.")
      return "redirect:/login/mfa"
    }
    upgradeAuthentication(email, request, response)
    return "redirect:/timer"
  }

  private fun requirePreMfaPrincipal(): PreMfaAuthenticationToken {
    val auth = SecurityContextHolder.getContext().authentication
    return auth as? PreMfaAuthenticationToken
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
  }

  private fun upgradeAuthentication(
      email: String,
      request: HttpServletRequest,
      response: HttpServletResponse,
  ) {
    val userDetails = userDetailsService.loadUserByUsername(email)
    val fullAuth = UsernamePasswordAuthenticationToken(userDetails, "", userDetails.authorities)
    val context = SecurityContextHolder.createEmptyContext()
    context.authentication = fullAuth
    SecurityContextHolder.setContext(context)
    securityContextRepository.saveContext(context, request, response)
  }
}
