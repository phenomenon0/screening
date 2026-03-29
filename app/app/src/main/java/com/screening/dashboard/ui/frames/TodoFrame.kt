package com.screening.dashboard.ui.frames

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.screening.dashboard.model.TodoItem
import com.screening.dashboard.ui.theme.*

@Composable
fun TodoFrame(
    items: List<TodoItem>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val (done, pending) = items.partition { it.done }
    val sorted = pending.sortedBy { it.priority } + done

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "To Do",
                style = DashboardTypography.headlineLarge
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "${pending.size} remaining",
                style = DashboardTypography.bodyLarge.copy(color = AccentCyan)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nothing to do!",
                    style = DashboardTypography.titleLarge.copy(color = TextDim)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sorted, key = { it.id }) { item ->
                    TodoRow(item)
                }
            }
        }
    }
}

@Composable
private fun TodoRow(item: TodoItem) {
    val priorityColor = when (item.priority) {
        1 -> AccentRed
        2 -> AccentAmber
        else -> AccentGreen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (item.done) DarkSurface.copy(alpha = 0.5f) else DarkCard)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Priority indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (item.done) TextDim else priorityColor)
        )
        Spacer(modifier = Modifier.width(16.dp))

        // Checkbox
        Text(
            text = if (item.done) "✓" else "○",
            style = DashboardTypography.titleLarge.copy(
                color = if (item.done) AccentGreen else TextSecondary
            )
        )
        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Text(
            text = item.text,
            style = DashboardTypography.titleLarge.copy(
                color = if (item.done) TextDim else TextPrimary,
                textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
