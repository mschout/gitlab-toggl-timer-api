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
package io.github.mschout.gitlab.toggltimer.toggl

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

class TogglProjectsStreamTest {

  private val client = mockk<TogglClient>()

  @Test
  fun `walks every page until an empty page is returned`() {
    val page1 = listOf(TogglProject(id = 1L, name = "one"), TogglProject(id = 2L, name = "two"))
    val page2 = listOf(TogglProject(id = 3L, name = "three"))
    every { client.getProjectsPaginated(7L, null) } returns page1
    every { client.getProjectsPaginated(7L, 2L) } returns page2
    every { client.getProjectsPaginated(7L, 3L) } returns emptyList()

    val result = client.streamProjects(7L).toList()

    result shouldContainExactly listOf(page1[0], page1[1], page2[0])
    verify(exactly = 1) { client.getProjectsPaginated(7L, null) }
    verify(exactly = 1) { client.getProjectsPaginated(7L, 2L) }
    verify(exactly = 1) { client.getProjectsPaginated(7L, 3L) }
  }

  @Test
  fun `returns empty stream when the first page is empty`() {
    every { client.getProjectsPaginated(7L, null) } returns emptyList()

    client.streamProjects(7L).toList() shouldBe emptyList()
    verify(exactly = 1) { client.getProjectsPaginated(7L, null) }
  }

  @Test
  fun `stops on 402 and returns whatever was already collected`() {
    val page1 = listOf(TogglProject(id = 10L, name = "first"))
    val page2 = listOf(TogglProject(id = 20L, name = "second"))
    every { client.getProjectsPaginated(7L, null) } returns page1
    every { client.getProjectsPaginated(7L, 10L) } returns page2
    every { client.getProjectsPaginated(7L, 20L) } throws
        HttpClientErrorException(
            HttpStatus.PAYMENT_REQUIRED,
            "Payment Required",
            HttpHeaders(),
            ByteArray(0),
            null,
        )

    val result = client.streamProjects(7L).toList()

    result shouldContainExactly listOf(page1[0], page2[0])
  }

  @Test
  fun `stops defensively when the page's last id is null`() {
    val page1 = listOf(TogglProject(id = null, name = "no-id"))
    every { client.getProjectsPaginated(7L, null) } returns page1

    client.streamProjects(7L).toList() shouldContainExactly page1
    verify(exactly = 1) { client.getProjectsPaginated(7L, null) }
  }

  @Test
  fun `does not call the API until the stream is consumed`() {
    every { client.getProjectsPaginated(7L, any()) } returns emptyList()

    client.streamProjects(7L)

    verify(exactly = 0) { client.getProjectsPaginated(any(), any()) }
  }
}
