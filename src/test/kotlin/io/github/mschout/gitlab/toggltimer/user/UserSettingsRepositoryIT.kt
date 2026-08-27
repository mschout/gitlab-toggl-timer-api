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
package io.github.mschout.gitlab.toggltimer.user

import io.github.mschout.gitlab.toggltimer.support.PostgresContainerSupport
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
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

  @Test
  fun `Toggl sync candidates include only enabled users with API keys`() {
    val eligible = userRepository.save(User(email = "sync-eligible@example.com"))
    val disabled = userRepository.save(User(email = "sync-disabled@example.com", enabled = false))
    val missingKey = userRepository.save(User(email = "sync-no-key@example.com"))
    try {
      userSettingsRepository.saveAllAndFlush(
          listOf(
              UserSettings(user = eligible, togglApiKey = "eligible-key"),
              UserSettings(user = disabled, togglApiKey = "disabled-key"),
              UserSettings(user = missingKey, togglApiKey = null),
          )
      )

      val candidateIds = userSettingsRepository.findAllEligibleForTogglSync().map { it.userId }

      candidateIds.shouldContain(eligible.id)
      candidateIds.shouldNotContain(disabled.id)
      candidateIds.shouldNotContain(missingKey.id)
    } finally {
      userRepository.deleteAllById(listOf(eligible.id, disabled.id, missingKey.id))
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
