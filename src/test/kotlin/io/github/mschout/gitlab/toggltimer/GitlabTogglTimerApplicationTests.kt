package io.github.mschout.gitlab.toggltimer

import io.github.mschout.gitlab.toggltimer.support.PostgresContainerSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class GitlabTogglTimerApplicationTests : PostgresContainerSupport() {
  @Test fun contextLoads() {}
}
