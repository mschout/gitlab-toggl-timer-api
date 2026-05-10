package io.github.mschout.gitlab.toggltimer.security

import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class LoginController(private val clientRegistrationRepository: ClientRegistrationRepository) {

  @GetMapping("/login")
  fun login(model: Model): String {
    val registrations: List<ClientRegistration> =
        @Suppress("UNCHECKED_CAST")
        (clientRegistrationRepository as? Iterable<ClientRegistration>)?.toList() ?: emptyList()
    model.addAttribute(
        "oidcProviders",
        registrations.map { mapOf("id" to it.registrationId, "name" to it.clientName) },
    )
    return "login"
  }
}
