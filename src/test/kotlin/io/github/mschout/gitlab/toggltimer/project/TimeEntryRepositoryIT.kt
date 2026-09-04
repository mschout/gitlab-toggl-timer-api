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
package io.github.mschout.gitlab.toggltimer.project

import io.github.mschout.gitlab.toggltimer.support.PostgresContainerSupport
import io.github.mschout.gitlab.toggltimer.user.User
import io.github.mschout.gitlab.toggltimer.user.UserRepository
import io.kotest.matchers.collections.shouldContainExactly
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Pageable

@SpringBootTest
class TimeEntryRepositoryIT
@Autowired
constructor(
    private val timeEntryRepository: TimeEntryRepository,
    private val userRepository: UserRepository,
) : PostgresContainerSupport() {

  @AfterEach
  fun cleanUp() {
    timeEntryRepository.deleteAll()
    userRepository.deleteAll()
  }

  @Test
  fun `finds latest completed stop in minute for user and excludes ineligible entries`() {
    val user = userRepository.save(User(email = "start-snap@example.com"))
    val otherUser = userRepository.save(User(email = "other-start-snap@example.com"))
    val minuteStart = Instant.parse("2026-09-03T19:22:00Z")
    val minuteEnd = Instant.parse("2026-09-03T19:23:00Z")

    timeEntryRepository.saveAll(
        listOf(
            entry(1L, user.id, stop = Instant.parse("2026-09-03T19:22:05Z")),
            entry(2L, user.id, stop = Instant.parse("2026-09-03T19:22:47Z")),
            entry(3L, user.id, stop = null, duration = -1L),
            entry(
                4L,
                user.id,
                stop = Instant.parse("2026-09-03T19:22:55Z"),
                serverDeletedAt = Instant.parse("2026-09-03T20:00:00Z"),
            ),
            entry(5L, otherUser.id, stop = Instant.parse("2026-09-03T19:22:58Z")),
            entry(6L, user.id, stop = minuteEnd),
        )
    )

    timeEntryRepository
        .findCompletedEndingInRange(user.id, minuteStart, minuteEnd, Pageable.ofSize(10))
        .map(TimeEntry::togglId)
        .shouldContainExactly(2L, 1L)
  }

  @Test
  fun `finds newest completed entry for color selection across workspaces`() {
    val user = userRepository.save(User(email = "completed-color@example.com"))
    val otherUser = userRepository.save(User(email = "other-completed-color@example.com"))

    timeEntryRepository.saveAll(
        listOf(
            entry(
                10L,
                user.id,
                stop = Instant.parse("2026-09-04T12:15:00Z"),
                start = Instant.parse("2026-09-04T12:00:00Z"),
            ),
            entry(
                20L,
                user.id,
                stop = Instant.parse("2026-09-04T13:15:00Z"),
                start = Instant.parse("2026-09-04T13:00:00Z"),
                workspaceId = 8L,
            ),
            entry(
                21L,
                user.id,
                stop = Instant.parse("2026-09-04T13:15:00Z"),
                start = Instant.parse("2026-09-04T13:00:00Z"),
                workspaceId = 9L,
            ),
            entry(
                30L,
                user.id,
                stop = null,
                duration = -1L,
                start = Instant.parse("2026-09-04T14:00:00Z"),
            ),
            entry(
                31L,
                user.id,
                stop = Instant.parse("2026-09-04T14:15:00Z"),
                duration = -1L,
                start = Instant.parse("2026-09-04T14:00:00Z"),
            ),
            entry(
                32L,
                user.id,
                stop = Instant.parse("2026-09-04T14:15:00Z"),
                start = Instant.parse("2026-09-04T14:00:00Z"),
                serverDeletedAt = Instant.parse("2026-09-04T15:00:00Z"),
            ),
            entry(
                33L,
                otherUser.id,
                stop = Instant.parse("2026-09-04T14:15:00Z"),
                start = Instant.parse("2026-09-04T14:00:00Z"),
            ),
        )
    )

    timeEntryRepository
        .findLatestCompletedForColorSelection(user.id, Pageable.ofSize(1))
        .map(TimeEntry::togglId)
        .shouldContainExactly(21L)
  }

  @Test
  fun `finds newest strict running entry for color selection across workspaces`() {
    val user = userRepository.save(User(email = "running-color@example.com"))
    val otherUser = userRepository.save(User(email = "other-running-color@example.com"))

    timeEntryRepository.saveAll(
        listOf(
            entry(
                40L,
                user.id,
                stop = null,
                duration = -1L,
                start = Instant.parse("2026-09-04T13:00:00Z"),
                workspaceId = 8L,
            ),
            entry(
                41L,
                user.id,
                stop = null,
                duration = -1L,
                start = Instant.parse("2026-09-04T13:00:00Z"),
                workspaceId = 9L,
            ),
            entry(
                50L,
                user.id,
                stop = null,
                duration = 0L,
                start = Instant.parse("2026-09-04T14:00:00Z"),
            ),
            entry(
                51L,
                user.id,
                stop = Instant.parse("2026-09-04T14:15:00Z"),
                duration = -1L,
                start = Instant.parse("2026-09-04T14:00:00Z"),
            ),
            entry(
                52L,
                user.id,
                stop = null,
                duration = -1L,
                start = Instant.parse("2026-09-04T14:00:00Z"),
                serverDeletedAt = Instant.parse("2026-09-04T15:00:00Z"),
            ),
            entry(
                53L,
                otherUser.id,
                stop = null,
                duration = -1L,
                start = Instant.parse("2026-09-04T14:00:00Z"),
            ),
        )
    )

    timeEntryRepository
        .findLatestRunningForColorSelection(user.id, Pageable.ofSize(1))
        .map(TimeEntry::togglId)
        .shouldContainExactly(41L)
  }

  private fun entry(
      togglId: Long,
      userId: Long,
      stop: Instant?,
      duration: Long = 60L,
      serverDeletedAt: Instant? = null,
      start: Instant = Instant.parse("2026-09-03T19:21:00Z"),
      workspaceId: Long = 7L,
  ): TimeEntry =
      TimeEntry(
          togglId = togglId,
          userId = userId,
          workspaceId = workspaceId,
          start = start,
          stop = stop,
          duration = duration,
          serverDeletedAt = serverDeletedAt,
      )
}
