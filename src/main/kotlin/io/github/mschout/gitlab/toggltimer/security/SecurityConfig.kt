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

import io.github.mschout.gitlab.toggltimer.mfa.MfaService
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.ObjectPostProcessor
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {

  @Bean
  fun securityFilterChain(
      http: HttpSecurity,
      customOidcUserService: CustomOidcUserService,
      onboardingFilter: OnboardingFilter,
      preMfaGuardFilter: PreMfaGuardFilter,
      authProperties: AuthProperties,
      userRepository: UserRepository,
      mfaService: MfaService,
  ): SecurityFilterChain {
    http.authorizeHttpRequests {
      it.requestMatchers(
              "/",
              "/login",
              "/login/mfa",
              "/login/webauthn",
              "/webauthn/**",
              "/error",
              "/css/**",
              "/static/**",
              "/webjars/**",
              "/actuator/health",
          )
          .permitAll()
          .anyRequest()
          .authenticated()
    }

    if (authProperties.passwordLoginEnabled) {
      http.formLogin {
        it.loginPage("/login")
            .successHandler(
                MfaAwareAuthenticationSuccessHandler(
                    userRepository = userRepository,
                    mfaService = mfaService,
                    defaultSuccessUrl = "/timer",
                )
            )
            .permitAll()
      }
    }

    http.objectPostProcessor(
        object : ObjectPostProcessor<Any> {
          override fun <O : Any> postProcess(obj: O): O {
            if (obj is WebAuthnAuthenticationFilter) {
              obj.setAuthenticationSuccessHandler(WebAuthnPrimaryLoginSuccessHandler("/timer"))
            }
            return obj
          }
        }
    )

    http
        .oauth2Login {
          it.loginPage("/login")
              .userInfoEndpoint { ui -> ui.oidcUserService(customOidcUserService) }
              .defaultSuccessUrl("/timer", true)
        }
        .webAuthn {
          it.rpName(authProperties.rpName)
              .rpId(authProperties.rpId)
              .allowedOrigins(authProperties.origins)
        }
        .logout { it.logoutSuccessUrl("/").permitAll() }
        .addFilterAfter(preMfaGuardFilter, AuthorizationFilter::class.java)
        .addFilterAfter(onboardingFilter, PreMfaGuardFilter::class.java)

    return http.build()
  }

  @Bean
  fun passwordEncoder(): PasswordEncoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder()
}
