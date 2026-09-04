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
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.ObjectPostProcessor
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {

  /**
   * This filter chain is ordered first so that static resources are served without any security
   * checks.
   */
  @Bean
  @Order(1)
  fun staticResourcesSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
        .securityMatcher(PathRequest.toStaticResources().atCommonLocations())
        .authorizeHttpRequests { it.anyRequest().permitAll() }
        .csrf { it.disable() }
        .requestCache { it.disable() }
        .securityContext { it.disable() }
        .sessionManagement { it.disable() }

    return http.build()
  }

  /**
   * This filter chain is ordered second so that all other requests are subject to security checks.
   */
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
              "/static/**",
              "/actuator/health",
          )
          .permitAll()
          .anyRequest()
          .authenticated()
    }

    // Use a JS-readable XSRF-TOKEN cookie so Swagger UI's request interceptor can pick
    // it up. The plain (non-XOR) request handler ensures the cookie value matches the
    // value the server expects in the X-XSRF-TOKEN header.
    http.csrf {
      it.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
          .csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
    }
    http.addFilterAfter(CsrfCookieFilter(), AuthorizationFilter::class.java)

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

// Forces the deferred CSRF token to materialise so the XSRF-TOKEN cookie is written on
// the first GET — without this, Swagger UI's request interceptor can't find the cookie.
private class CsrfCookieFilter : OncePerRequestFilter() {
  override fun doFilterInternal(
      request: HttpServletRequest,
      response: HttpServletResponse,
      filterChain: FilterChain,
  ) {
    (request.getAttribute(CsrfToken::class.java.name) as? CsrfToken)?.token
    filterChain.doFilter(request, response)
  }
}
