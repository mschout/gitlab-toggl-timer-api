package io.github.mschout.gitlab.toggltimer.security

import io.github.mschout.gitlab.toggltimer.timer.TimerService
import io.github.mschout.gitlab.toggltimer.timer.TimerWebController
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.github.mschout.gitlab.toggltimer.user.User
import io.github.mschout.gitlab.toggltimer.user.UserAuthIdentityRepository
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.github.mschout.gitlab.toggltimer.user.UserSettings
import io.github.mschout.gitlab.toggltimer.user.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import java.util.Optional
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
    private val configuredUser = User(email = "alice@example.com", id = 42L)
    private val configuredSettings =
        UserSettings(
            user = configuredUser,
            gitlabAccessToken = "alice-gitlab",
            togglApiKey = "alice-toggl",
            togglWorkspaceId = 7L,
            userId = 42L,
        )

    @Bean fun timerService(): TimerService = mockk(relaxed = true)

    @Bean fun currentUserCredentialsService(): CurrentUserCredentialsService = mockk(relaxed = true)

    @Bean fun restTemplateBuilder(): RestTemplateBuilder = RestTemplateBuilder()

    @Bean
    fun userRepository(): UserRepository =
        mockk<UserRepository>(relaxed = true).also {
          every { it.findByEmail("alice@example.com") } returns configuredUser
        }

    @Bean fun userAuthIdentityRepository(): UserAuthIdentityRepository = mockk(relaxed = true)

    @Bean
    fun userSettingsRepository(): UserSettingsRepository =
        mockk<UserSettingsRepository>(relaxed = true).also {
          every { it.findById(42L) } returns Optional.of(configuredSettings)
        }

    @Bean
    fun customOidcUserService(): CustomOidcUserService =
        CustomOidcUserService(userRepository(), userAuthIdentityRepository())

    @Bean
    fun customUserDetailsService(): CustomUserDetailsService =
        CustomUserDetailsService(userRepository())

    @Bean
    fun onboardingFilter(): OnboardingFilter =
        OnboardingFilter(userRepository(), userSettingsRepository())
  }

  @Test
  fun `unauthenticated GET timer redirects to login`() {
    mvc.perform(get("/timer"))
        .andExpect(status().is3xxRedirection)
        .andExpect(redirectedUrl("/login"))
  }

  @Test
  fun `authenticated GET timer is allowed when user has settings`() {
    mvc.perform(get("/timer").with(user("alice@example.com").roles("USER")))
        .andExpect(status().isOk)
  }

  @Test
  fun `unauthenticated GET root is permitted`() {
    mvc.perform(get("/")).andExpect(status().isOk)
  }
}
