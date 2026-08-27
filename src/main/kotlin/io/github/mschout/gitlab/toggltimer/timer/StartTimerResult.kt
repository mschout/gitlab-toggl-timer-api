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

import java.time.Instant

data class StartTimerResult(
    val togglId: Long,
    val startTime: Instant,
    val projectName: String?,
    val description: String?,
    val clientName: String? = null,
    val projectColor: String? = null,
)

data class RunningTimerView(
    val startTime: Instant,
    val descriptionEditor: TimeEntryDescriptionEditorView,
    val projectPicker: TimeEntryProjectPickerView,
)
