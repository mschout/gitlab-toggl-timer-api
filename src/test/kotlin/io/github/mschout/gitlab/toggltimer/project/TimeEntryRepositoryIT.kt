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

  private fun entry(
      togglId: Long,
      userId: Long,
      stop: Instant?,
      duration: Long = 60L,
      serverDeletedAt: Instant? = null,
  ): TimeEntry =
      TimeEntry(
          togglId = togglId,
          userId = userId,
          workspaceId = 7L,
          start = Instant.parse("2026-09-03T19:21:00Z"),
          stop = stop,
          duration = duration,
          serverDeletedAt = serverDeletedAt,
      )
}
