package com.screening.dashboard.ui.frames

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable
fun HabitFrame(
    habits: List<HabitInfo>,
    modifier: Modifier = Modifier
) {
    if (habits.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            Text("No habits yet", style = DashboardTypography.titleLarge.copy(color = Outline))
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(start = 24.dp, end = 48.dp, top = 8.dp, bottom = 24.dp)
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(text = "Habits", style = DashboardTypography.headlineLarge)
            Spacer(modifier = Modifier.width(16.dp))
            val activeCount = habits.count { it.doneToday }
            val completionPct = if (habits.isNotEmpty()) (activeCount * 100 / habits.size) else 0
            Text(
                text = "${habits.size} Active \u2022 ${completionPct}% Completion Today",
                style = DashboardTypography.bodyMedium.copy(color = Outline)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        // 2x2 grid
        val rows = habits.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            rows.take(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { habit ->
                        Box(modifier = Modifier.weight(1f)) { HabitCard(habit) }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HabitCard(habit: HabitInfo) {
    val historySet = remember(habit.history) { habit.history.toSet() }
    val streakColor = when {
        habit.streak >= 30 -> PrimaryContainer
        habit.streak >= 7 -> TertiaryContainer
        habit.streak > 0 -> Amber
        else -> Outline
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerHigh)
            .padding(20.dp)
    ) {
        // Top: name + streak
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(text = habit.name, style = DashboardTypography.titleLarge, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (habit.streak > 0) Text(text = "\uD83D\uDD25", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "${habit.streak}", style = DashboardTypography.headlineMedium.copy(color = streakColor))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status badge
        val (badgeText, badgeColor) = when {
            habit.doneToday -> "DONE TODAY" to TertiaryContainer
            else -> "PENDING" to Amber
        }
        Box(
            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                .background(badgeColor.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(text = badgeText, style = DashboardTypography.labelSmall.copy(color = badgeColor))
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Total: ${habit.totalDone}", style = DashboardTypography.bodySmall)
        Text(text = "Best Streak: ${habit.bestStreak}", style = DashboardTypography.bodySmall)

        Spacer(modifier = Modifier.height(12.dp))

        // Mini heatmap
        MiniHeatmap(historySet = historySet, doneToday = habit.doneToday)
    }
}

@Composable
private fun MiniHeatmap(historySet: Set<String>, doneToday: Boolean) {
    val today = remember { LocalDate.now() }
    val weeks = 8
    val days = weeks * 7

    val grid = remember(historySet, today) {
        val startDate = today.minusDays((days - 1).toLong())
        (0 until days).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val done = historySet.contains(date.toString()) || (date == today && doneToday)
            date to done
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(weeks) { week ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { dayOfWeek ->
                    val idx = week * 7 + dayOfWeek
                    if (idx < grid.size) {
                        val (date, done) = grid[idx]
                        val isToday = date == today
                        Box(
                            modifier = Modifier.size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        done && isToday -> PrimaryContainer
                                        done -> TertiaryContainer
                                        isToday -> SurfaceContainerHighest
                                        else -> SurfaceContainer
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}
