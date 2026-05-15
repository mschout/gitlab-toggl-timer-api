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

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository

@Configuration
class WebAuthnRepositoryConfig {

  @Bean
  fun userCredentialRepository(jdbcOperations: JdbcOperations): UserCredentialRepository =
      JdbcUserCredentialRepository(jdbcOperations)

  @Bean
  fun publicKeyCredentialUserEntityRepository(
      jdbcOperations: JdbcOperations
  ): PublicKeyCredentialUserEntityRepository =
      JdbcPublicKeyCredentialUserEntityRepository(jdbcOperations)
}
