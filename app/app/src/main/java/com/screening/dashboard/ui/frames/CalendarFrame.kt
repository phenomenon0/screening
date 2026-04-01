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

    // Upcoming = tomorrow through 7 days out (exclude today)
    val upcomingEvents = remember(events) {
        val tomorrow = today.plusDays(1)
        val weekEnd = today.plusDays(7)
        events.filter { ev ->
            try { val d = OffsetDateTime.parse(ev.start).toLocalDate(); d in tomorrow..weekEnd } catch (_: Exception) { false }
        }.sortedBy { it.start }
    }

    Row(modifier = modifier.fillMaxSize().padding(start = 24.dp, end = 48.dp, top = 8.dp, bottom = 24.dp)) {
        // Left: Today
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                Text(text = "No events today", style = DashboardTypography.bodyLarge.copy(color = Outline))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(todayEvents) { event -> TodayEventCard(event) }
                }
            }
        }

        Spacer(modifier = Modifier.width(32.dp))

        // Right: Upcoming Week
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text(text = "Upcoming Week", style = DashboardTypography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))

            if (upcomingEvents.isEmpty()) {
                Text(text = "Nothing scheduled", style = DashboardTypography.bodyLarge.copy(color = Outline))
            } else {
                val grouped = remember(upcomingEvents) {
                    upcomingEvents.groupBy {
                        try { OffsetDateTime.parse(it.start).toLocalDate() } catch (_: Exception) { today }
                    }.toSortedMap()
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    grouped.forEach { (date, dayEvents) ->
                        item {
                            val dayName = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
                            val dayNum = date.dayOfMonth
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "$dayName $dayNum", style = DashboardTypography.titleMedium.copy(color = OnSurface))
                                Text(text = "${dayEvents.size}", style = DashboardTypography.labelMedium.copy(color = Outline))
                            }
                        }
                        items(dayEvents) { event ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(PrimaryContainer))
                                Spacer(modifier = Modifier.width(8.dp))
                                val time = try {
                                    if (event.allDay) "All Day"
                                    else OffsetDateTime.parse(event.start).format(DateTimeFormatter.ofPattern("h:mm a"))
                                } catch (_: Exception) { "" }
                                Text(text = time, style = DashboardTypography.labelMedium.copy(color = PrimaryContainer), modifier = Modifier.width(64.dp))
                                Text(text = event.title, style = DashboardTypography.bodyMedium.copy(color = OnSurface), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayEventCard(event: CalendarEvent) {
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
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                .background(PrimaryContainer.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(text = timeStr, style = DashboardTypography.labelMedium.copy(color = PrimaryContainer))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = event.title, style = DashboardTypography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
