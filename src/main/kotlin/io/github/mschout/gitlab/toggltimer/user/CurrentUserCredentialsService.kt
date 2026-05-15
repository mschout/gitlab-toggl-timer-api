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

  fun currentTogglWorkspaceId(): Long? = currentSettings()?.togglWorkspaceId

  fun requireGitlabToken(): String =
      currentSettings()?.gitlabAccessToken?.takeIf { it.isNotBlank() }
          ?: throw MissingCredentialsException("gitlab")

  fun requireTogglApiKey(): String =
      currentSettings()?.togglApiKey?.takeIf { it.isNotBlank() }
          ?: throw MissingCredentialsException("toggl")
}
