package io.github.mschout.gitlab.toggltimer.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {

  @Bean
  fun securityFilterChain(
      http: HttpSecurity,
      customOidcUserService: CustomOidcUserService,
      onboardingFilter: OnboardingFilter,
  ): SecurityFilterChain =
      http
          .authorizeHttpRequests {
            it.requestMatchers(
                    "/",
                    "/login",
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
          .formLogin { it.loginPage("/login").defaultSuccessUrl("/timer", true).permitAll() }
          .oauth2Login {
            it.loginPage("/login")
                .userInfoEndpoint { ui -> ui.oidcUserService(customOidcUserService) }
                .defaultSuccessUrl("/timer", true)
          }
          .logout { it.logoutSuccessUrl("/").permitAll() }
          .addFilterAfter(onboardingFilter, AuthorizationFilter::class.java)
          .build()

  @Bean
  fun passwordEncoder(): PasswordEncoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder()
}
