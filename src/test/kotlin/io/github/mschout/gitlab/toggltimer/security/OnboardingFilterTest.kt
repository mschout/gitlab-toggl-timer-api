package io.github.mschout.gitlab.toggltimer.security

import io.github.mschout.gitlab.toggltimer.user.User
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.github.mschout.gitlab.toggltimer.user.UserSettings
import io.github.mschout.gitlab.toggltimer.user.UserSettingsRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import java.util.Optional
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder

class OnboardingFilterTest {

  private val userRepo = mockk<UserRepository>()
  private val settingsRepo = mockk<UserSettingsRepository>()
  private val filter = OnboardingFilter(userRepo, settingsRepo)
  private val chain = mockk<FilterChain>(relaxed = true)

  @AfterEach
  fun clearAuth() {
    SecurityContextHolder.clearContext()
  }

  private fun authenticateAs(email: String) {
    SecurityContextHolder.getContext().authentication =
        UsernamePasswordAuthenticationToken(
            email,
            "n/a",
            AuthorityUtils.createAuthorityList("ROLE_USER"),
        )
  }

  private fun request(path: String) =
      MockHttpServletRequest("GET", path).apply { requestURI = path }

  @Test
  fun `passes through when no authentication`() {
    val req = request("/timer")
    val res = MockHttpServletResponse()

    filter.doFilter(req, res, chain)

    verify { chain.doFilter(req, res) }
    res.redirectedUrl shouldBe null
  }

  @Test
  fun `passes through when anonymous`() {
    SecurityContextHolder.getContext().authentication =
        AnonymousAuthenticationToken(
            "anon",
            "anon",
            AuthorityUtils.createAuthorityList("ROLE_ANON"),
        )
    val req = request("/timer")
    val res = MockHttpServletResponse()

    filter.doFilter(req, res, chain)

    verify { chain.doFilter(req, res) }
  }

  @Test
  fun `redirects to settings when no UserSettings row exists`() {
    val user = User(email = "alice@example.com", id = 1L)
    authenticateAs("alice@example.com")
    every { userRepo.findByEmail("alice@example.com") } returns user
    every { settingsRepo.findById(1L) } returns Optional.empty()
    val res = MockHttpServletResponse()

    filter.doFilter(request("/timer"), res, chain)

    res.redirectedUrl shouldBe "/settings"
    verify(exactly = 0) { chain.doFilter(any(), any()) }
  }

  @Test
  fun `redirects to settings when one credential is blank`() {
    val user = User(email = "alice@example.com", id = 1L)
    authenticateAs("alice@example.com")
    every { userRepo.findByEmail("alice@example.com") } returns user
    every { settingsRepo.findById(1L) } returns
        Optional.of(
            UserSettings(user = user, gitlabAccessToken = "x", togglApiKey = null, userId = 1L)
        )
    val res = MockHttpServletResponse()

    filter.doFilter(request("/timer"), res, chain)

    res.redirectedUrl shouldBe "/settings"
  }

  @Test
  fun `redirects to settings when workspace id missing`() {
    val user = User(email = "alice@example.com", id = 1L)
    authenticateAs("alice@example.com")
    every { userRepo.findByEmail("alice@example.com") } returns user
    every { settingsRepo.findById(1L) } returns
        Optional.of(
            UserSettings(
                user = user,
                gitlabAccessToken = "x",
                togglApiKey = "y",
                togglWorkspaceId = null,
                userId = 1L,
            )
        )
    val res = MockHttpServletResponse()

    filter.doFilter(request("/timer"), res, chain)

    res.redirectedUrl shouldBe "/settings"
    verify(exactly = 0) { chain.doFilter(any(), any()) }
  }

  @Test
  fun `passes through when all settings configured`() {
    val user = User(email = "alice@example.com", id = 1L)
    authenticateAs("alice@example.com")
    every { userRepo.findByEmail("alice@example.com") } returns user
    every { settingsRepo.findById(1L) } returns
        Optional.of(
            UserSettings(
                user = user,
                gitlabAccessToken = "x",
                togglApiKey = "y",
                togglWorkspaceId = 42L,
                userId = 1L,
            )
        )
    val req = request("/timer")
    val res = MockHttpServletResponse()

    filter.doFilter(req, res, chain)

    verify { chain.doFilter(req, res) }
    res.redirectedUrl shouldBe null
  }

  @Test
  fun `skips filter for whitelisted settings path even without credentials`() {
    authenticateAs("alice@example.com")
    val req = request("/settings")
    val res = MockHttpServletResponse()

    filter.doFilter(req, res, chain)

    verify { chain.doFilter(req, res) }
    verify(exactly = 0) { userRepo.findByEmail(any()) }
  }

  @Test
  fun `skips filter for static webjars`() {
    authenticateAs("alice@example.com")
    val req = request("/webjars/bootstrap/css/x.css")
    val res = MockHttpServletResponse()

    filter.doFilter(req, res, chain)

    verify { chain.doFilter(req, res) }
    verify(exactly = 0) { userRepo.findByEmail(any()) }
  }
}
