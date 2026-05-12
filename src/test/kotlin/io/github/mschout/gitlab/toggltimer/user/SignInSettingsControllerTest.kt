package io.github.mschout.gitlab.toggltimer.user

import io.github.mschout.gitlab.toggltimer.security.AuthProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.ui.ExtendedModelMap
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap

class SignInSettingsControllerTest {

  private val credentialsService = mockk<CurrentUserCredentialsService>()
  private val userRepository = mockk<UserRepository>()
  private val passwordEncoder = mockk<PasswordEncoder>()

  private fun controller(passwordLoginEnabled: Boolean = true) =
      SignInSettingsController(
          credentialsService,
          userRepository,
          passwordEncoder,
          AuthProperties(
              passwordLoginEnabled = passwordLoginEnabled,
              rpName = "Test",
              rpId = "localhost",
              origins = setOf("http://localhost:8080"),
          ),
      )

  @Test
  fun `show renders form and reports no password when user has none`() {
    every { credentialsService.currentUser() } returns User(email = "a@b.com", id = 1L)
    val model = ExtendedModelMap()

    val view = controller().show(model)

    view shouldBe "settings/sign-in"
    model["form"] shouldBe PasswordChangeForm()
    model["hasPassword"] shouldBe false
  }

  @Test
  fun `show reports hasPassword true when user already has a password`() {
    every { credentialsService.currentUser() } returns
        User(email = "a@b.com", passwordHash = "{bcrypt}existing", id = 1L)
    val model = ExtendedModelMap()

    controller().show(model)

    model["hasPassword"] shouldBe true
  }

  @Test
  fun `show throws 404 when password login is disabled`() {
    val ex =
        shouldThrow<ResponseStatusException> {
          controller(passwordLoginEnabled = false).show(ExtendedModelMap())
        }
    ex.statusCode shouldBe HttpStatus.NOT_FOUND
  }

  @Test
  fun `save sets password for first-time user without requiring current password`() {
    val user = User(email = "a@b.com", id = 1L)
    every { credentialsService.currentUser() } returns user
    every { passwordEncoder.encode("a-good-long-password") } returns "{bcrypt}hashed"
    val saved = slot<User>()
    every { userRepository.save(capture(saved)) } answers { saved.captured }
    val redirectAttrs = RedirectAttributesModelMap()

    val view =
        controller()
            .save(
                PasswordChangeForm(
                    newPassword = "a-good-long-password",
                    confirmPassword = "a-good-long-password",
                ),
                redirectAttrs,
            )

    view shouldBe "redirect:/settings/sign-in"
    saved.captured.passwordHash shouldBe "{bcrypt}hashed"
    redirectAttrs.flashAttributes["success"] shouldBe
        "Password set. You can now sign in with email and password."
  }

  @Test
  fun `save rejects too-short password`() {
    val user = User(email = "a@b.com", id = 1L)
    every { credentialsService.currentUser() } returns user
    val redirectAttrs = RedirectAttributesModelMap()

    val view =
        controller()
            .save(
                PasswordChangeForm(newPassword = "tooshort", confirmPassword = "tooshort"),
                redirectAttrs,
            )

    view shouldBe "redirect:/settings/sign-in"
    redirectAttrs.flashAttributes["error"] shouldBe "Password must be at least 12 characters."
    verify(exactly = 0) { userRepository.save(any()) }
  }

  @Test
  fun `save rejects mismatched confirmation`() {
    val user = User(email = "a@b.com", id = 1L)
    every { credentialsService.currentUser() } returns user
    val redirectAttrs = RedirectAttributesModelMap()

    val view =
        controller()
            .save(
                PasswordChangeForm(
                    newPassword = "a-good-long-password",
                    confirmPassword = "a-good-long-password-typo",
                ),
                redirectAttrs,
            )

    view shouldBe "redirect:/settings/sign-in"
    redirectAttrs.flashAttributes["error"] shouldBe "Passwords do not match."
    verify(exactly = 0) { userRepository.save(any()) }
  }

  @Test
  fun `save rejects change when current password does not match`() {
    val user = User(email = "a@b.com", passwordHash = "{bcrypt}existing", id = 1L)
    every { credentialsService.currentUser() } returns user
    every { passwordEncoder.matches("wrong", "{bcrypt}existing") } returns false
    val redirectAttrs = RedirectAttributesModelMap()

    val view =
        controller()
            .save(
                PasswordChangeForm(
                    currentPassword = "wrong",
                    newPassword = "a-good-long-password",
                    confirmPassword = "a-good-long-password",
                ),
                redirectAttrs,
            )

    view shouldBe "redirect:/settings/sign-in"
    redirectAttrs.flashAttributes["error"] shouldBe "Current password is incorrect."
    verify(exactly = 0) { userRepository.save(any()) }
  }

  @Test
  fun `save rejects change when current password is blank`() {
    val user = User(email = "a@b.com", passwordHash = "{bcrypt}existing", id = 1L)
    every { credentialsService.currentUser() } returns user
    val redirectAttrs = RedirectAttributesModelMap()

    val view =
        controller()
            .save(
                PasswordChangeForm(
                    currentPassword = "",
                    newPassword = "a-good-long-password",
                    confirmPassword = "a-good-long-password",
                ),
                redirectAttrs,
            )

    view shouldBe "redirect:/settings/sign-in"
    redirectAttrs.flashAttributes["error"] shouldBe "Current password is incorrect."
    verify(exactly = 0) { userRepository.save(any()) }
  }

  @Test
  fun `save updates password when current password matches`() {
    val user = User(email = "a@b.com", passwordHash = "{bcrypt}existing", id = 1L)
    every { credentialsService.currentUser() } returns user
    every { passwordEncoder.matches("old-correct-password", "{bcrypt}existing") } returns true
    every { passwordEncoder.encode("new-correct-password") } returns "{bcrypt}new"
    val saved = slot<User>()
    every { userRepository.save(capture(saved)) } answers { saved.captured }
    val redirectAttrs = RedirectAttributesModelMap()

    val view =
        controller()
            .save(
                PasswordChangeForm(
                    currentPassword = "old-correct-password",
                    newPassword = "new-correct-password",
                    confirmPassword = "new-correct-password",
                ),
                redirectAttrs,
            )

    view shouldBe "redirect:/settings/sign-in"
    saved.captured.passwordHash shouldBe "{bcrypt}new"
    redirectAttrs.flashAttributes["success"] shouldBe "Password updated."
  }

  @Test
  fun `save throws 404 when password login is disabled`() {
    val ex =
        shouldThrow<ResponseStatusException> {
          controller(passwordLoginEnabled = false)
              .save(PasswordChangeForm(), RedirectAttributesModelMap())
        }
    ex.statusCode shouldBe HttpStatus.NOT_FOUND
  }
}
