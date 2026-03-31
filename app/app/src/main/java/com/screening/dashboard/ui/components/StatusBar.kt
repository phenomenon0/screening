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

@Composable
fun StatusBar(
    connected: Boolean,
    nowPlaying: String? = null,
    modifier: Modifier = Modifier
) {
    var timeStr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm"))
            delay(30_000)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 48.dp, top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: app name + connection
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "TV Dashboard",
                style = DashboardTypography.titleMedium.copy(color = OnSurface)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (connected) TertiaryContainer else AccentRed)
            )
        }

        // Center: now playing
        if (nowPlaying != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PrimaryContainer)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Now Playing: ${nowPlaying.substringBeforeLast(".")}",
                    style = DashboardTypography.labelMedium.copy(color = OnSurfaceVariant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right: clock
        Text(
            text = timeStr,
            style = DashboardTypography.headlineMedium.copy(
                color = PrimaryContainer,
                fontSize = 28.sp
            )
        )
    }
}
