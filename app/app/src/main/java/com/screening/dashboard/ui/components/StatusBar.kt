package com.screening.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun StatusBar(
    connected: Boolean,
    nowPlaying: String? = null,
    modifier: Modifier = Modifier
) {
    var timeStr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))
            delay(30_000)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (connected) AccentGreen else AccentRed)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (connected) "Connected" else "Disconnected",
                style = DashboardTypography.labelSmall
            )

            if (nowPlaying != null) {
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    text = "\u266A ${nowPlaying.substringBeforeLast(".")}",
                    style = DashboardTypography.labelSmall.copy(color = AccentCyan),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = timeStr,
            style = DashboardTypography.bodyMedium.copy(color = TextSecondary)
        )
    }
}
