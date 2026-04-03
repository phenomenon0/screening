package com.screening.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val statusFont = DashboardTypography.titleMedium.copy(
    fontFamily = ManropeFamily,
    fontSize = 18.sp
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

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        // Left: weather
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!connected) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AccentRed))
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (weatherEmoji.isNotEmpty()) {
                Text(
                    text = "$weatherEmoji $weatherTemp",
                    style = statusFont.copy(color = OnSurface)
                )
            }
        }

        // Right: clock
        Text(
            text = timeStr,
            style = statusFont.copy(color = OnSurfaceVariant),
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}
