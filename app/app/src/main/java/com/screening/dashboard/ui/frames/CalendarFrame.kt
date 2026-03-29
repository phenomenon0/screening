package com.screening.dashboard.ui.frames

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.screening.shared.model.CalendarEvent
import com.screening.dashboard.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

@Composable
fun CalendarFrame(events: List<CalendarEvent>, modifier: Modifier = Modifier) {
    val today = remember { LocalDate.now() }
    val todayEvents = remember(events) {
        events.filter { ev ->
            try {
                val dt = OffsetDateTime.parse(ev.start)
                dt.toLocalDate() == today
            } catch (_: Exception) { false }
        }.sortedBy { it.start }
    }

    val weekEvents = remember(events) {
        val weekStart = today
        val weekEnd = today.plusDays(7)
        events.filter { ev ->
            try {
                val dt = OffsetDateTime.parse(ev.start).toLocalDate()
                dt in weekStart..weekEnd
            } catch (_: Exception) { false }
        }.sortedBy { it.start }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        // Left: Today's schedule
        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
        ) {
            Text(
                text = "Today",
                style = DashboardTypography.headlineLarge
            )
            Text(
                text = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                style = DashboardTypography.bodyLarge.copy(color = AccentCyan)
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (todayEvents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No events today",
                        style = DashboardTypography.titleLarge.copy(color = TextDim)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(todayEvents) { event ->
                        EventCard(event)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(32.dp))

        // Right: Week ahead
        Column(
            modifier = Modifier
                .weight(0.8f)
                .fillMaxHeight()
        ) {
            Text(
                text = "This Week",
                style = DashboardTypography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(weekEvents) { event ->
                    WeekEventRow(event)
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent) {
    val timeStr = remember(event.start, event.end) {
        if (event.allDay) "All Day"
        else {
            try {
                val start = OffsetDateTime.parse(event.start)
                val end = OffsetDateTime.parse(event.end)
                val fmt = DateTimeFormatter.ofPattern("h:mm a")
                "${start.format(fmt)} – ${end.format(fmt)}"
            } catch (_: Exception) { "" }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AccentCyan)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = event.title,
                style = DashboardTypography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = timeStr,
                style = DashboardTypography.bodyMedium.copy(color = AccentCyan)
            )
        }
    }
}

@Composable
private fun WeekEventRow(event: CalendarEvent) {
    val dateStr = remember(event.start) {
        try {
            val dt = OffsetDateTime.parse(event.start)
            val day = dt.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
            val time = if (event.allDay) "" else dt.format(DateTimeFormatter.ofPattern(" h:mm a"))
            "$day$time"
        } catch (_: Exception) { "" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateStr,
            style = DashboardTypography.bodyMedium.copy(color = AccentCyan),
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = event.title,
            style = DashboardTypography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
