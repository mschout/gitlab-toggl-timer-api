package io.github.mschout.gitlab.toggltimer.user

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CurrentUserCredentialsService(
    private val userRepository: UserRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {

  fun currentUserOrNull(): User? {
    val auth = SecurityContextHolder.getContext().authentication ?: return null
    if (!auth.isAuthenticated || auth is AnonymousAuthenticationToken) return null
    val email = auth.name?.takeIf { it.isNotBlank() } ?: return null
    return userRepository.findByEmail(email)
  }

  fun currentUser(): User =
      currentUserOrNull() ?: throw IllegalStateException("No authenticated user in SecurityContext")

  fun currentUserId(): Long = currentUser().id

  @Transactional(readOnly = true)
  fun currentSettings(): UserSettings? =
      currentUserOrNull()?.let { userSettingsRepository.findById(it.id).orElse(null) }

  fun requireGitlabToken(): String =
      currentSettings()?.gitlabAccessToken?.takeIf { it.isNotBlank() }
          ?: throw MissingCredentialsException("gitlab")

  fun requireTogglApiKey(): String =
      currentSettings()?.togglApiKey?.takeIf { it.isNotBlank() }
          ?: throw MissingCredentialsException("toggl")
}
