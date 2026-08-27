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
package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.project.TogglTimeEntrySyncPersistenceService
import io.github.mschout.gitlab.toggltimer.project.TogglTimeEntrySyncState
import io.github.mschout.gitlab.toggltimer.project.TogglTimeEntrySyncStateRepository
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.user.User
import io.github.mschout.gitlab.toggltimer.user.UserSettings
import io.github.mschout.gitlab.toggltimer.user.UserSettingsRepository
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TogglTimeEntrySyncJobTest {

  private val syncedThrough = Instant.parse("2026-08-27T18:00:00Z")
  private lateinit var userSettingsRepository: UserSettingsRepository
  private lateinit var syncStateRepository: TogglTimeEntrySyncStateRepository
  private lateinit var togglClientFactory: TogglClientFactory
  private lateinit var persistenceService: TogglTimeEntrySyncPersistenceService
  private lateinit var properties: TogglTimeEntrySyncProperties
  private lateinit var job: TogglTimeEntrySyncJob

  @BeforeEach
  fun setUp() {
    userSettingsRepository = mockk()
    syncStateRepository = mockk()
    togglClientFactory = mockk()
    persistenceService = mockk()
    properties = TogglTimeEntrySyncProperties()
    job =
        TogglTimeEntrySyncJob(
            userSettingsRepository = userSettingsRepository,
            syncStateRepository = syncStateRepository,
            togglClientFactory = togglClientFactory,
            persistenceService = persistenceService,
            properties = properties,
            clock = Clock.fixed(syncedThrough, ZoneOffset.UTC),
        )
  }

  @Test
  fun `configuration defaults to an enabled fifteen minute sync with seven day lookback`() {
    assertSoftly {
      properties.enabled shouldBe true
      properties.interval shouldBe Duration.ofMinutes(15)
      properties.initialDelay shouldBe Duration.ofSeconds(30)
      properties.initialLookback shouldBe Duration.ofDays(7)
    }
  }

  @Test
  fun `first sync uses the initial lookback and advances through the run start`() {
    val settings = settings(userId = 42L, apiKey = "tog-secret")
    val client = mockk<TogglClient>()
    val entries = listOf(TogglTimeEntry(id = 123L))
    val expectedSince = syncedThrough.minus(Duration.ofDays(7)).minusSeconds(1).epochSecond
    every { userSettingsRepository.findAllEligibleForTogglSync() } returns listOf(settings)
    every { syncStateRepository.findById(42L) } returns Optional.empty()
    every { togglClientFactory.forApiKey("tog-secret") } returns client
    every { client.getModifiedTimeEntries(since = expectedSince, meta = true) } returns entries
    every {
      persistenceService.persistAndAdvance(
          userId = 42L,
          entries = entries,
          syncedThrough = syncedThrough,
      )
    } just Runs

    job.syncTimeEntries()

    verify {
      persistenceService.persistAndAdvance(
          userId = 42L,
          entries = entries,
          syncedThrough = syncedThrough,
      )
    }
  }

  @Test
  fun `later sync overlaps the stored cursor by one second`() {
    val previousSync = Instant.parse("2026-08-27T17:45:00Z")
    val settings = settings(userId = 42L, apiKey = "tog-secret")
    val client = mockk<TogglClient>()
    every { userSettingsRepository.findAllEligibleForTogglSync() } returns listOf(settings)
    every { syncStateRepository.findById(42L) } returns
        Optional.of(TogglTimeEntrySyncState(42L, previousSync))
    every { togglClientFactory.forApiKey("tog-secret") } returns client
    every {
      client.getModifiedTimeEntries(since = previousSync.minusSeconds(1).epochSecond, meta = true)
    } returns emptyList()
    every { persistenceService.persistAndAdvance(42L, emptyList(), syncedThrough) } just Runs

    job.syncTimeEntries()

    verify { persistenceService.persistAndAdvance(42L, emptyList(), syncedThrough) }
  }

  @Test
  fun `failure for one user does not prevent the next user from syncing`() {
    val firstClient = mockk<TogglClient>()
    val secondClient = mockk<TogglClient>()
    val expectedSince = syncedThrough.minus(Duration.ofDays(7)).minusSeconds(1).epochSecond
    every { userSettingsRepository.findAllEligibleForTogglSync() } returns
        listOf(settings(1L, "bad-key"), settings(2L, "good-key"))
    every { syncStateRepository.findById(any()) } returns Optional.empty()
    every { togglClientFactory.forApiKey("bad-key") } returns firstClient
    every { togglClientFactory.forApiKey("good-key") } returns secondClient
    every { firstClient.getModifiedTimeEntries(expectedSince, true) } throws
        IllegalStateException("unauthorized")
    every { secondClient.getModifiedTimeEntries(expectedSince, true) } returns emptyList()
    every { persistenceService.persistAndAdvance(2L, emptyList(), syncedThrough) } just Runs

    job.syncTimeEntries()

    verify(exactly = 0) { persistenceService.persistAndAdvance(1L, any(), any()) }
    verify { persistenceService.persistAndAdvance(2L, emptyList(), syncedThrough) }
  }

  @Test
  fun `response at the Toggl limit does not persist or advance the cursor`() {
    val client = mockk<TogglClient>()
    every { userSettingsRepository.findAllEligibleForTogglSync() } returns
        listOf(settings(42L, "tog-secret"))
    every { syncStateRepository.findById(42L) } returns Optional.empty()
    every { togglClientFactory.forApiKey("tog-secret") } returns client
    every { client.getModifiedTimeEntries(any(), true) } returns
        List(1_000) { TogglTimeEntry(id = it.toLong()) }

    job.syncTimeEntries()

    verify(exactly = 0) { persistenceService.persistAndAdvance(any(), any(), any()) }
  }

  @Test
  fun `blank API keys are ignored defensively`() {
    every { userSettingsRepository.findAllEligibleForTogglSync() } returns
        listOf(settings(42L, "  "))

    job.syncTimeEntries()

    verify(exactly = 0) { syncStateRepository.findById(any()) }
    verify(exactly = 0) { togglClientFactory.forApiKey(any()) }
  }

  private fun settings(userId: Long, apiKey: String): UserSettings {
    val user = User(email = "user-$userId@example.com", id = userId)
    return UserSettings(user = user, togglApiKey = apiKey, userId = userId)
  }
}
