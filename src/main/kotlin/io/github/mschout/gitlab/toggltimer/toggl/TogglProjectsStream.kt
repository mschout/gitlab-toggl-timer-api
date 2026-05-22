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

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.stream.Stream
import kotlin.streams.asStream
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

private val logger = KotlinLogging.logger {}

/**
 * Lazily walks every page of `/workspaces/{id}/projects/paginated`, emitting projects until the API
 * returns an empty page. A `402 Payment Required` response halts iteration early and the stream
 * terminates with the projects already emitted — callers receive whatever was collected so far.
 */
fun TogglClient.streamProjects(workspaceId: Long): Stream<TogglProject> =
    sequence {
          var cursor: Long? = null
          while (true) {
            val page =
                try {
                  getProjectsPaginated(workspaceId, cursor)
                } catch (e: HttpClientErrorException) {
                  if (e.statusCode != HttpStatus.PAYMENT_REQUIRED) throw e
                  logger.warn {
                    "Toggl returned 402 Payment Required while paginating projects for workspace $workspaceId; stopping with partial results"
                  }
                  break
                }
            if (page.isEmpty()) break
            yieldAll(page)
            val lastId = page.last().id
            // Defensive: if Toggl can't advance the cursor we'd loop forever.
            if (lastId == null || lastId == cursor) break
            cursor = lastId
          }
        }
        .asStream()
