package io.github.mschout.gitlab.toggltimer.timer

data class StopTimerResult(val durationSeconds: Long, val durationFormatted: String) {
  companion object {
    fun formatHms(seconds: Long): String {
      val clamped = if (seconds < 0) 0 else seconds
      val h = clamped / 3600
      val m = (clamped % 3600) / 60
      val s = clamped % 60
      return "%02d:%02d:%02d".format(h, m, s)
    }
  }
}
