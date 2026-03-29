package com.screening.dashboard.ui.frames

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.screening.shared.model.HabitInfo
import com.screening.dashboard.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitFrame(
    habits: List<HabitInfo>,
    modifier: Modifier = Modifier
) {
    if (habits.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Text("No habits yet", style = DashboardTypography.titleLarge.copy(color = TextDim))
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items(habits, key = { it.id }) { habit ->
            HabitCard(habit)
        }
    }
}

@Composable
private fun HabitCard(habit: HabitInfo) {
    val historySet = remember(habit.history) { habit.history.toSet() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(24.dp)
    ) {
        // Header: name + streak
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = habit.name,
                    style = DashboardTypography.headlineMedium
                )
                Text(
                    text = "${habit.totalDone} total completions",
                    style = DashboardTypography.bodyMedium.copy(color = TextDim)
                )
            }

            // Streak badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (habit.streak > 0) {
                    Text(
                        text = "\uD83D\uDD25",  // fire emoji
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${habit.streak}",
                        style = DashboardTypography.headlineLarge.copy(
                            color = when {
                                habit.streak >= 30 -> AccentCyan
                                habit.streak >= 7 -> AccentGreen
                                habit.streak > 0 -> AccentAmber
                                else -> TextDim
                            },
                            fontSize = 48.sp
                        )
                    )
                    Text(
                        text = "day streak",
                        style = DashboardTypography.labelSmall
                    )
                    if (habit.bestStreak > habit.streak) {
                        Text(
                            text = "best: ${habit.bestStreak}",
                            style = DashboardTypography.labelSmall.copy(color = TextDim)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Heatmap: last 12 weeks (84 days)
        HeatmapGrid(historySet = historySet, doneToday = habit.doneToday)

        // Status
        if (habit.doneToday) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Done today",
                style = DashboardTypography.bodyMedium.copy(color = AccentGreen)
            )
        }
    }
}

@Composable
private fun HeatmapGrid(historySet: Set<String>, doneToday: Boolean) {
    val today = remember { LocalDate.now() }
    val weeks = 12
    val days = weeks * 7

    // Build grid: 7 rows (Mon-Sun) x 12 columns (weeks)
    val grid = remember(historySet, today) {
        val startDate = today.minusDays((days - 1).toLong())
        (0 until days).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val dateStr = date.toString() // YYYY-MM-DD
            val done = historySet.contains(dateStr) || (date == today && doneToday)
            Triple(date, dateStr, done)
        }
    }

    Column {
        // Week day labels
        Row {
            Spacer(modifier = Modifier.width(0.dp))
            // 7 rows for each day of week
        }

        // Grid
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(weeks) { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(7) { dayOfWeek ->
                        val idx = week * 7 + dayOfWeek
                        if (idx < grid.size) {
                            val (date, _, done) = grid[idx]
                            val isToday = date == today
                            val color = when {
                                done && isToday -> AccentCyan
                                done -> AccentGreen
                                isToday -> DarkSurface
                                else -> Color(0xFF1A1A1A)
                            }
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }

        // Month labels
        Spacer(modifier = Modifier.height(4.dp))
        val months = remember(today) {
            val startDate = today.minusDays((days - 1).toLong())
            (0 until weeks).mapNotNull { week ->
                val date = startDate.plusDays((week * 7).toLong())
                if (date.dayOfMonth <= 7) {
                    date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                } else null
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            months.forEach { month ->
                Text(text = month, style = DashboardTypography.labelSmall)
            }
        }
    }
}
