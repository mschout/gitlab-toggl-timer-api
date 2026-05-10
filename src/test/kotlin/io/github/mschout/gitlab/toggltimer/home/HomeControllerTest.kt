package io.github.mschout.gitlab.toggltimer.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeControllerTest {

  @Test
  fun `index returns the index view`() {
    HomeController().index() shouldBe "index"
  }
}
