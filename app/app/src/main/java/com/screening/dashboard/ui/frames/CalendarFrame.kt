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
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.screening.shared.model.CalendarEvent
import com.screening.dashboard.ui.theme.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

@Composable
fun CalendarFrame(events: List<CalendarEvent>, modifier: Modifier = Modifier) {
    val today = remember { LocalDate.now() }
    val todayEvents = remember(events) {
        events.filter { ev ->
            try { OffsetDateTime.parse(ev.start).toLocalDate() == today } catch (_: Exception) { false }
        }.sortedBy { it.start }
    }
    val weekEvents = remember(events) {
        val weekEnd = today.plusDays(7)
        events.filter { ev ->
            try { val d = OffsetDateTime.parse(ev.start).toLocalDate(); d in today..weekEnd } catch (_: Exception) { false }
        }.sortedBy { it.start }
    }

    Row(modifier = modifier.fillMaxSize().padding(start = 24.dp, end = 48.dp, top = 8.dp, bottom = 24.dp)) {
        // Left: Today
        Column(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
            Text(
                text = today.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.getDefault()),
                style = DashboardTypography.displayLarge
            )
            Text(
                text = today.format(DateTimeFormatter.ofPattern("MMMM d")),
                style = DashboardTypography.headlineMedium.copy(color = PrimaryContainer)
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (todayEvents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No events today", style = DashboardTypography.titleLarge.copy(color = Outline))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(todayEvents) { event -> EventCard(event) }
                }
            }
        }

        Spacer(modifier = Modifier.width(32.dp))

        // Right: Upcoming Week
        Column(modifier = Modifier.weight(0.8f).fillMaxHeight()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Upcoming Week", style = DashboardTypography.titleLarge)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Group by day
            val grouped = remember(weekEvents) {
                weekEvents.groupBy {
                    try { OffsetDateTime.parse(it.start).toLocalDate() } catch (_: Exception) { today }
                }.toSortedMap()
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                grouped.forEach { (date, dayEvents) ->
                    item {
                        val dayName = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
                        val dayNum = date.dayOfMonth
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "$dayName $dayNum", style = DashboardTypography.titleMedium.copy(color = OnSurface))
                            Text(text = "${dayEvents.size} Event${if (dayEvents.size > 1) "s" else ""}", style = DashboardTypography.bodySmall)
                        }
                    }
                    items(dayEvents) { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainer)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(PrimaryContainer))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = event.title, style = DashboardTypography.bodyMedium.copy(color = OnSurface), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val timeStr = if (event.allDay) "All Day" else try {
                                    OffsetDateTime.parse(event.start).format(DateTimeFormatter.ofPattern("h:mm a"))
                                } catch (_: Exception) { "" }
                                Text(text = timeStr, style = DashboardTypography.bodySmall.copy(color = PrimaryContainer))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent) {
    val timeStr = remember(event.start, event.end) {
        if (event.allDay) "ALL DAY"
        else try {
            val s = OffsetDateTime.parse(event.start); val e = OffsetDateTime.parse(event.end)
            val fmt = DateTimeFormatter.ofPattern("HH:mm")
            "${s.format(fmt)} \u2014 ${e.format(fmt)}"
        } catch (_: Exception) { "" }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerHigh)
            .padding(20.dp)
    ) {
        // Time badge
        Box(
            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                .background(PrimaryContainer.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(text = timeStr, style = DashboardTypography.labelMedium.copy(color = PrimaryContainer))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = event.title, style = DashboardTypography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
