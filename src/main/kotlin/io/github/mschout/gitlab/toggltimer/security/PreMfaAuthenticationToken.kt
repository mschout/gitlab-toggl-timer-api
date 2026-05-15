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

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Partial-authentication token used between successful password verification and successful MFA
 * verification. Carries the verified principal but exposes only [ROLE_PRE_MFA] so the user cannot
 * reach any protected resource until MFA completes.
 */
class PreMfaAuthenticationToken(
    private val principalDetails: UserDetails,
    val pendingAuthorities: Collection<GrantedAuthority>,
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority(ROLE_PRE_MFA))) {

  init {
    isAuthenticated = true
  }

  override fun getCredentials(): Any = ""

  override fun getPrincipal(): Any = principalDetails

  override fun getName(): String = principalDetails.username

  companion object {
    const val ROLE_PRE_MFA = "ROLE_PRE_MFA"
  }
}
