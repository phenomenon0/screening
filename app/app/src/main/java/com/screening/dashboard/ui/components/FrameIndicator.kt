package com.screening.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.screening.dashboard.ui.theme.PrimaryContainer
import com.screening.dashboard.ui.theme.OutlineVariant

@Composable
fun FrameIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == current) 10.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (index == current) PrimaryContainer else OutlineVariant)
            )
            if (index < count - 1) Spacer(modifier = Modifier.width(6.dp))
        }
    }
}
