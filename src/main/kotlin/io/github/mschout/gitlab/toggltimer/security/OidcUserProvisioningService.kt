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
package io.github.mschout.gitlab.toggltimer.security

import io.github.mschout.gitlab.toggltimer.user.User
import io.github.mschout.gitlab.toggltimer.user.UserAuthIdentity
import io.github.mschout.gitlab.toggltimer.user.UserAuthIdentityRepository
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class OidcUserProvisioningService(
    private val userRepository: UserRepository,
    private val identityRepository: UserAuthIdentityRepository,
) {
  @Transactional
  fun findOrCreate(provider: String, subject: String, email: String, displayName: String?): User {
    identityRepository.findByProviderAndSubject(provider, subject)?.let { identity ->
      // Re-fetch by id to load the eagerly-mapped roles used for login authorities.
      return userRepository.findById(identity.user.id).orElseThrow {
        IllegalStateException("OIDC identity ${identity.id} references missing user")
      }
    }

    val user =
        userRepository.findByEmail(email)
            ?: userRepository.save(User(email = email, displayName = displayName)).also {
              log.info {
                "Provisioned new OIDC user id=${it.id} email=${it.email} provider=$provider"
              }
            }

    if (displayName != null && user.displayName == null) {
      user.displayName = displayName
    }

    identityRepository.save(UserAuthIdentity(provider = provider, subject = subject, user = user))
    return user
  }
}
