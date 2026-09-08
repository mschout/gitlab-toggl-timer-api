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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.EnableTransactionManagement

@ExtendWith(OutputCaptureExtension::class)
class CustomOidcUserServiceStartupTest {
  @Test
  fun `starts with transaction management without final method proxy warnings`(
      output: CapturedOutput
  ) {
    AnnotationConfigApplicationContext(TestConfig::class.java).use { context ->
      val service = context.getBean(CustomOidcUserService::class.java)
      output.all shouldNotContain "cannot get proxied via CGLIB"

      val users = context.getBean(UserRepository::class.java)
      val identities = context.getBean(UserAuthIdentityRepository::class.java)
      val transactions = context.getBean(PlatformTransactionManager::class.java)
      val status = mockk<TransactionStatus>()
      var transactionStarted = false
      every { transactions.getTransaction(any()) } answers
          {
            transactionStarted = true
            status
          }
      every { transactions.commit(status) } returns Unit
      every { identities.findByProviderAndSubject("oidc", "sub-1") } answers
          {
            transactionStarted shouldBe true
            null
          }
      every { users.findByEmail("alice@example.com") } returns null
      every { users.save(any<User>()) } answers { firstArg() }
      every { identities.save(any<UserAuthIdentity>()) } answers { firstArg() }

      val registration =
          ClientRegistration.withRegistrationId("oidc")
              .clientId("test-client")
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("https://app.example.com/login/oauth2/code/oidc")
              .authorizationUri("https://id.example.com/authorize")
              .tokenUri("https://id.example.com/token")
              .scope("openid", "email")
              .build()
      val issuedAt = Instant.parse("2026-09-08T12:00:00Z")
      val expiresAt = issuedAt.plusSeconds(300)
      val token =
          OidcIdToken(
              "id-token",
              issuedAt,
              expiresAt,
              mapOf("sub" to "sub-1", "email" to "alice@example.com"),
          )
      val accessToken =
          OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", issuedAt, expiresAt)

      val user = service.loadUser(OidcUserRequest(registration, accessToken, token))

      user.name shouldBe "alice@example.com"
      user.authorities.map { it.authority }.toSet() shouldBe setOf("ROLE_USER", "OIDC_USER")
      verify(exactly = 1) { transactions.commit(status) }
      verify(exactly = 1) { identities.save(any<UserAuthIdentity>()) }
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement(proxyTargetClass = true)
  @Import(CustomOidcUserService::class, OidcUserProvisioningService::class)
  class TestConfig {
    @Bean fun userRepository(): UserRepository = mockk()

    @Bean fun identityRepository(): UserAuthIdentityRepository = mockk()

    @Bean fun transactionManager(): PlatformTransactionManager = mockk()
  }
}
