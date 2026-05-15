package io.github.mschout.gitlab.toggltimer.timer

import java.time.Instant

data class StartTimerResult(
    val startTime: Instant,
    val projectName: String?,
    val description: String?,
)
