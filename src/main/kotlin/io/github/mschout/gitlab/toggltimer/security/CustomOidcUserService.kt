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

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.oidc.StandardClaimNames
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import org.springframework.stereotype.Service

@Service
class CustomOidcUserService(private val provisioningService: OidcUserProvisioningService) :
    OidcUserService() {

  override fun loadUser(userRequest: OidcUserRequest): OidcUser {
    val oidcUser = super.loadUser(userRequest)
    val provider = userRequest.clientRegistration.registrationId
    val subject =
        requireNotNull(oidcUser.subject) {
          "OIDC userinfo missing 'sub' claim — provider $provider returned no subject identifier"
        }
    val email =
        requireNotNull(oidcUser.email) {
          "OIDC userinfo missing 'email' claim — ensure the provider is configured to release the email scope"
        }
    val displayName = oidcUser.fullName ?: oidcUser.preferredUsername

    val user =
        provisioningService.findOrCreate(
            provider = provider,
            subject = subject,
            email = email,
            displayName = displayName,
        )

    val authorities: Collection<GrantedAuthority> =
        user.roles.map { SimpleGrantedAuthority(it) } +
            OidcUserAuthority(oidcUser.idToken, oidcUser.userInfo)

    return DefaultOidcUser(
        authorities,
        oidcUser.idToken,
        oidcUser.userInfo,
        StandardClaimNames.EMAIL,
    )
  }
}
