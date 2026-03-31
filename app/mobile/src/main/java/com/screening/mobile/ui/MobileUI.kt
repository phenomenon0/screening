package com.screening.mobile.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import com.screening.shared.data.DashboardRepository
import com.screening.shared.model.DashboardState

@Composable
fun MobileUI(
    state: DashboardState,
    repo: DashboardRepository,
    imageLoader: ImageLoader,
    serverBaseUrl: String
) {
    var currentTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Remote", "Todos", "Habits", "Timer")
    val tabIcons = listOf("📺", "✅", "🔥", "⏱")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (state.connected) Green else Red)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.connected) "Connected" else "Disconnected",
                    style = MobileType.label
                )
            }
            Text("Screening", style = MobileType.label.copy(color = Cyan))
        }

        // Content
        Box(modifier = Modifier.weight(1f)) {
            when (currentTab) {
                0 -> RemoteTab(state, repo)
                1 -> TodosTab(state, repo)
                2 -> HabitsTab(state, repo)
                3 -> TimerTab(state, repo)
            }
        }

        // Bottom nav
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { i, label ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { currentTab = i }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(tabIcons[i], fontSize = 20.sp)
                    Text(
                        label,
                        style = MobileType.label.copy(
                            color = if (i == currentTab) Cyan else Dim
                        )
                    )
                }
            }
        }
    }
}

// ── REMOTE TAB ──
@Composable
fun RemoteTab(state: DashboardState, repo: DashboardRepository) {
    val context = LocalContext.current
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TV Remote", style = MobileType.h1)
        Spacer(Modifier.height(24.dp))

        // Swipe area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffset > 100) {
                                repo.changeFrame(1) // next
                                haptic(context)
                            } else if (dragOffset < -100) {
                                repo.changeFrame(0) // prev - simplified
                                haptic(context)
                            }
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, delta -> dragOffset += delta }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text("Swipe to change frame", style = MobileType.body)
        }

        Spacer(Modifier.height(16.dp))

        // Frame buttons
        val frames = listOf("📷 Gallery" to 0, "✅ Todos" to 1, "🔥 Habits" to 2, "📅 Calendar" to 3, "🎬 Video" to 4)
        frames.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                row.forEach { (label, idx) ->
                    Button(
                        onClick = {
                            repo.changeFrame(idx)
                            haptic(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Card),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, fontSize = 13.sp, color = TextW)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Connected devices
        if (state.devices.isNotEmpty()) {
            Text("Connected Devices", style = MobileType.h2)
            state.devices.forEach { d ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Card)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (d.type == "tv") "📺" else "📱", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(d.name, style = MobileType.body.copy(color = TextW))
                }
            }
        }
    }
}

// ── TODOS TAB ──
@Composable
fun TodosTab(state: DashboardState, repo: DashboardRepository) {
    var newText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Todos", style = MobileType.h1)
        Spacer(Modifier.height(12.dp))

        // Add input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = newText,
                onValueChange = { newText = it },
                textStyle = TextStyle(color = TextW, fontSize = 16.sp),
                cursorBrush = SolidColor(Cyan),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Card)
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (newText.isEmpty()) Text("Add task...", color = Dim, fontSize = 16.sp)
                    inner()
                }
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newText.isNotBlank()) {
                        repo.addTodo(newText.trim(), 2)
                        newText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Cyan)
            ) { Text("Add", color = DarkBg) }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val sorted = state.todos.sortedWith(compareBy({ it.done }, { it.priority }))
            items(sorted, key = { it.id }) { todo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (todo.done) Card.copy(alpha = 0.5f) else Card)
                        .clickable { repo.toggleTodo(todo.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (todo.done) "✓" else "○",
                        color = if (todo.done) Green else TextG,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        todo.text,
                        style = MobileType.body.copy(
                            color = if (todo.done) Dim else TextW,
                            textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val pColor = when (todo.priority) { 1 -> Red; 2 -> Amber; else -> Green }
                    Text("P${todo.priority}", color = pColor, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "✕",
                        color = Red,
                        modifier = Modifier.clickable { repo.deleteTodo(todo.id) },
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

// ── HABITS TAB ──
@Composable
fun HabitsTab(state: DashboardState, repo: DashboardRepository) {
    val context = LocalContext.current
    var newName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Habits", style = MobileType.h1)
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = newName,
                onValueChange = { newName = it },
                textStyle = TextStyle(color = TextW, fontSize = 16.sp),
                cursorBrush = SolidColor(Cyan),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Card)
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (newName.isEmpty()) Text("New habit...", color = Dim, fontSize = 16.sp)
                    inner()
                }
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newName.isNotBlank()) { repo.addHabit(newName.trim()); newName = "" }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Cyan)
            ) { Text("Add", color = DarkBg) }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.habits, key = { it.id }) { habit ->
                val streakColor = when {
                    habit.streak >= 30 -> Cyan
                    habit.streak >= 7 -> Green
                    habit.streak > 0 -> Amber
                    else -> Dim
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Card)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Streak
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                        Text(
                            "${if (habit.streak > 0) "\uD83D\uDD25" else ""} ${habit.streak}",
                            color = streakColor,
                            fontSize = 22.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text("streak", style = MobileType.label)
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(habit.name, style = MobileType.body.copy(color = TextW))
                        Text("${habit.totalDone} total · best: ${habit.bestStreak}", style = MobileType.label)
                    }

                    Button(
                        onClick = {
                            repo.toggleHabit(habit.id)
                            haptic(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (habit.doneToday) Red else Green
                        )
                    ) {
                        Text(if (habit.doneToday) "Undo" else "Done", color = DarkBg)
                    }
                }
            }
        }
    }
}

// ── TIMER TAB ──
@Composable
fun TimerTab(state: DashboardState, repo: DashboardRepository) {
    val p = state.pomodoro ?: return Box(
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentAlignment = Alignment.Center
    ) { Text("Connecting...", style = MobileType.body) }

    val mins = p.remaining / 60
    val secs = p.remaining % 60
    val color = when {
        p.remaining == 0 -> Green
        p.remaining < 60 -> Red
        p.running -> Cyan
        else -> TextW
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pomodoro", style = MobileType.h1)
        Spacer(Modifier.height(48.dp))

        Text(
            "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}",
            fontSize = 72.sp,
            color = color,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Thin
        )

        Text(
            when {
                p.remaining == 0 -> "Done!"
                p.running -> "Focus..."
                else -> "Ready"
            },
            style = MobileType.body,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { repo.pomodoroStart() }, colors = ButtonDefaults.buttonColors(containerColor = Cyan)) {
                Text("▶ Start", color = DarkBg)
            }
            Button(onClick = { repo.pomodoroPause() }, colors = ButtonDefaults.buttonColors(containerColor = Amber)) {
                Text("⏸ Pause", color = DarkBg)
            }
            Button(onClick = { repo.pomodoroReset() }, colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                Text("↺ Reset", color = DarkBg)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 15, 25, 50).forEach { m ->
                OutlinedButton(onClick = { repo.pomodoroSet(m) }) {
                    Text("${m}m", color = TextG)
                }
            }
        }
    }
}

private fun haptic(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
