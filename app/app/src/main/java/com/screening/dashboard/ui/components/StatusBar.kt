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
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Unified status bar font — Manrope SemiBold 18sp, legible from across the room
private val statusFont = DashboardTypography.titleMedium.copy(
    fontFamily = ManropeFamily,
    fontSize = 14.sp
)

@Composable
fun StatusBar(
    connected: Boolean,
    nowPlaying: String? = null,
    weatherEmoji: String = "",
    weatherTemp: String = "",
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
            .background(Background.copy(alpha = 0.7f))
            .padding(horizontal = 48.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: weather + connection
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (connected) TertiaryContainer else AccentRed)
            )
            if (weatherEmoji.isNotEmpty()) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "$weatherEmoji $weatherTemp",
                    style = statusFont.copy(color = OnSurface)
                )
            }
        }

        // Center: now playing
        if (nowPlaying != null) {
            Text(
                text = "\u266A ${nowPlaying.substringBeforeLast(".")}",
                style = statusFont.copy(color = OnSurfaceVariant),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 300.dp)
            )
        }

        // Right: clock
        Text(
            text = timeStr,
            style = statusFont.copy(color = OnSurfaceVariant)
        )
    }
}
