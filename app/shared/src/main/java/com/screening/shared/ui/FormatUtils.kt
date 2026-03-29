package com.screening.shared.ui

object PomodoroFormatter {
    fun formatTime(remaining: Int): String {
        val mins = remaining / 60
        val secs = remaining % 60
        return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    }

    fun statusLabel(remaining: Int, duration: Int, running: Boolean): String = when {
        remaining == 0 -> "Done!"
        running -> "Focus"
        remaining == duration -> "Ready"
        else -> "Paused"
    }

    fun statusColorIndex(remaining: Int, running: Boolean): Int = when {
        remaining == 0 -> 0    // green
        remaining < 60 -> 1   // red
        running -> 2           // cyan
        else -> 3              // text
    }
}

object HabitUtils {
    fun streakColorIndex(streak: Int): Int = when {
        streak >= 30 -> 0  // cyan
        streak >= 7 -> 1   // green
        streak > 0 -> 2    // amber
        else -> 3           // dim
    }
}
