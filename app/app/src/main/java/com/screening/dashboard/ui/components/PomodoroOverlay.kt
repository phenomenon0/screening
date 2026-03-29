package com.screening.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.screening.shared.model.PomodoroInfo
import com.screening.dashboard.ui.theme.*

@Composable
fun PomodoroOverlay(
    pomodoro: PomodoroInfo,
    modifier: Modifier = Modifier
) {
    if (!pomodoro.running && pomodoro.remaining == pomodoro.duration) return // not started

    val mins = pomodoro.remaining / 60
    val secs = pomodoro.remaining % 60
    val timeStr = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    val pct = if (pomodoro.duration > 0) pomodoro.remaining.toFloat() / pomodoro.duration else 1f

    val color = when {
        pomodoro.remaining == 0 -> AccentGreen
        pomodoro.remaining < 60 -> AccentRed
        pomodoro.running -> AccentCyan
        else -> TextSecondary
    }

    val label = when {
        pomodoro.remaining == 0 -> "Done!"
        pomodoro.running -> "Focus"
        else -> "Paused"
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCard.copy(alpha = 0.9f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Progress bar
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DarkSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(pct)
                        .background(color)
                )
            }

            Text(
                text = timeStr,
                style = DashboardTypography.titleLarge.copy(
                    color = color,
                    fontSize = 28.sp
                )
            )

            Text(
                text = label,
                style = DashboardTypography.labelSmall.copy(color = color)
            )
        }
    }
}
