package com.screening.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.screening.dashboard.ui.theme.*

private val frameIcons = listOf(
    "\uD83D\uDDBC",  // Gallery (framed picture)
    "\u2705",         // Todo (check)
    "\uD83D\uDD25",   // Habits (fire)
    "\uD83D\uDCC5",   // Calendar
    "\uD83C\uDFAC",   // Video (clapper)
    "\uD83C\uDFB5",   // Music (note)
)

@Composable
fun NavRail(
    frameCount: Int,
    currentFrame: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(64.dp)
            .background(SurfaceContainerLow.copy(alpha = 0.6f))
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        repeat(frameCount.coerceAtMost(frameIcons.size)) { index ->
            val isActive = index == currentFrame
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isActive) Modifier.background(SurfaceContainerHigh)
                        else Modifier
                    )
                    .graphicsLayer {
                        scaleX = if (isActive) 1.1f else 1f
                        scaleY = if (isActive) 1.1f else 1f
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = frameIcons.getOrElse(index) { "" },
                    style = DashboardTypography.bodyLarge.copy(
                        color = if (isActive) PrimaryContainer else Outline
                    )
                )
            }
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PrimaryContainer)
                )
            }
        }
    }
}
