package io.github.mschout.gitlab.toggltimer.user

import io.github.mschout.gitlab.toggltimer.support.PostgresContainerSupport
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UserSettingsRepositoryIT
@Autowired
constructor(
    private val userRepository: UserRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val dataSource: DataSource,
) : PostgresContainerSupport() {

  @Test
  fun `gitlab token is encrypted at rest and decrypted on read`() {
    val user = userRepository.save(User(email = "encrypt-it@example.com"))
    try {
      val plaintext = "glpat-secret-value-12345"

      userSettingsRepository.saveAndFlush(
          UserSettings(user = user, gitlabAccessToken = plaintext, togglApiKey = "tog-secret")
      )

      val rawCiphertext = readRawColumn(user.id, "gitlab_access_token_encrypted")
      rawCiphertext shouldNotBe null
      rawCiphertext shouldNotBe plaintext

      val reloaded = userSettingsRepository.findById(user.id).orElseThrow()
      reloaded.gitlabAccessToken shouldBe plaintext
      reloaded.togglApiKey shouldBe "tog-secret"
    } finally {
      userRepository.deleteById(user.id)
    }
  }

  private fun readRawColumn(userId: Long, column: String): String? {
    dataSource.connection.use { conn ->
      conn.prepareStatement("SELECT $column FROM user_settings WHERE user_id = ?").use { stmt ->
        stmt.setLong(1, userId)
        stmt.executeQuery().use { rs ->
          return if (rs.next()) rs.getString(1) else null
        }
      }
    }
  }
}
