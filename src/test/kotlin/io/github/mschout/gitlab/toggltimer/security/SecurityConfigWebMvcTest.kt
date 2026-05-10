package io.github.mschout.gitlab.toggltimer.security

import io.github.mschout.gitlab.toggltimer.timer.TimerService
import io.github.mschout.gitlab.toggltimer.timer.TimerWebController
import io.github.mschout.gitlab.toggltimer.user.UserAuthIdentityRepository
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [TimerWebController::class])
@Import(SecurityConfig::class, SecurityConfigWebMvcTest.MockBeans::class)
class SecurityConfigWebMvcTest(@Autowired val mvc: MockMvc) {

  @TestConfiguration
  class MockBeans {
    @Bean fun timerService(): TimerService = mockk(relaxed = true)

    @Bean fun restTemplateBuilder(): RestTemplateBuilder = RestTemplateBuilder()

    @Bean fun userRepository(): UserRepository = mockk(relaxed = true)

    @Bean fun userAuthIdentityRepository(): UserAuthIdentityRepository = mockk(relaxed = true)

    @Bean
    fun customOidcUserService(): CustomOidcUserService =
        CustomOidcUserService(userRepository(), userAuthIdentityRepository())

    @Bean
    fun customUserDetailsService(): CustomUserDetailsService =
        CustomUserDetailsService(userRepository())
  }

  @Test
  fun `unauthenticated GET timer redirects to login`() {
    mvc.perform(get("/timer"))
        .andExpect(status().is3xxRedirection)
        .andExpect(redirectedUrl("/login"))
  }

  @Test
  fun `authenticated GET timer is allowed`() {
    mvc.perform(get("/timer").with(user("alice@example.com").roles("USER")))
        .andExpect(status().isOk)
  }

  @Test
  fun `unauthenticated GET root is permitted`() {
    mvc.perform(get("/")).andExpect(status().isOk)
  }
}
