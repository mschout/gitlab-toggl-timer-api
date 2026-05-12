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
