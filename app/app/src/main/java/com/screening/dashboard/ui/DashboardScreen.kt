package com.screening.dashboard.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.screening.shared.model.DashboardState
import com.screening.dashboard.ui.components.FrameIndicator
import com.screening.dashboard.ui.components.PomodoroOverlay
import com.screening.dashboard.ui.components.StatusBar
import com.screening.dashboard.ui.frames.*
import androidx.tv.material3.Text
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.delay

private const val FRAME_IMAGES = 0
private const val FRAME_TODO = 1
private const val FRAME_HABITS = 2
private const val FRAME_CALENDAR = 3
private const val FRAME_VIDEOS = 4
private const val FRAME_MUSIC = 5
private const val FRAME_AI = 6
private const val FRAME_PRESENT = 7
private const val AUTO_FRAME_COUNT = 4
private const val TOTAL_FRAMES = 8
private val INTERACTIVE_FRAMES = setOf(FRAME_TODO, FRAME_VIDEOS, FRAME_MUSIC, FRAME_PRESENT)

private fun frameDurationMs(frame: Int): Long = when (frame) {
    FRAME_IMAGES -> 8 * 60_000L
    FRAME_TODO -> 2 * 60_000L
    FRAME_HABITS -> 60_000L
    FRAME_CALENDAR -> 60_000L
    else -> 60_000L
}

@Composable
fun DashboardScreen(
    state: DashboardState,
    imageLoader: ImageLoader,
    onToggleTodo: (String) -> Unit,
    onToggleHabit: (String) -> Unit = {},
    onAddTodo: (String, Int) -> Unit = { _, _ -> },
    onScreenShareStop: () -> Unit = {},
    onClearForceFrame: () -> Unit = {},
    onClearAlarm: () -> Unit = {},
    onClearAiQuery: () -> Unit = {},
    onPresentPage: (Int) -> Unit = {},
    onPresentClose: () -> Unit = {}
) {
    val screenUrl = state.screenShareUrl
    if (screenUrl != null) {
        ScreenShareFrame(streamUrl = screenUrl, onExit = onScreenShareStop)
        BackHandler { onScreenShareStop() }
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var activeFrame by remember { mutableIntStateOf(FRAME_IMAGES) }
    val focusRequester = remember { FocusRequester() }
    val hasVideos = state.videos.isNotEmpty()
    val hasMusic = state.music.isNotEmpty()

    LaunchedEffect(state.forceFrame) {
        state.forceFrame?.let { activeFrame = it.coerceIn(0, TOTAL_FRAMES - 1); onClearForceFrame() }
    }

    // Alarm overlay
    var showAlarm by remember { mutableStateOf(false) }
    var alarmTitle by remember { mutableStateOf("") }
    var alarmTime by remember { mutableStateOf("") }
    val alarmPlayer = remember { android.media.MediaPlayer.create(context, com.screening.dashboard.R.raw.alarm_alert) }

    LaunchedEffect(state.alarmTitle) {
        if (state.alarmTitle != null && !showAlarm) {
            alarmTitle = state.alarmTitle!!
            alarmTime = state.alarmTime ?: ""
            showAlarm = true
            try { alarmPlayer.start() } catch (_: Exception) {}
            delay(20_000)
            showAlarm = false
            try { alarmPlayer.pause(); alarmPlayer.seekTo(0) } catch (_: Exception) {}
            onClearAlarm()
        }
    }

    DisposableEffect(Unit) { onDispose { try { alarmPlayer.release() } catch (_: Exception) {} } }

    var autoScrollKey by remember { mutableIntStateOf(0) }
    var aiOkTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(autoScrollKey, activeFrame) {
        if (activeFrame < AUTO_FRAME_COUNT) { delay(frameDurationMs(activeFrame)); activeFrame = (activeFrame + 1) % AUTO_FRAME_COUNT }
    }

    // Re-assert focus on the outer box every time frame changes.
    // Without this, LazyColumn inside ChatFrame (and other frames) steals focus on TV.
    LaunchedEffect(activeFrame) { focusRequester.requestFocus() }

    // Auto-switch to AI frame when phone sends a query
    LaunchedEffect(state.aiQuery) {
        if (state.aiQuery != null) { activeFrame = FRAME_AI; autoScrollKey++ }
    }

    // Auto-switch to presentation frame when content arrives
    LaunchedEffect(state.presentation?.id) {
        if (state.presentation != null) { activeFrame = FRAME_PRESENT; autoScrollKey++ }
    }

    fun navigateFrame(delta: Int) {
        var next = (activeFrame + delta + TOTAL_FRAMES) % TOTAL_FRAMES
        if (next == FRAME_VIDEOS && !hasVideos) next = (next + delta + TOTAL_FRAMES) % TOTAL_FRAMES
        if (next == FRAME_MUSIC && !hasMusic) next = (next + delta + TOTAL_FRAMES) % TOTAL_FRAMES
        activeFrame = next; autoScrollKey++
    }

    BackHandler(enabled = activeFrame >= AUTO_FRAME_COUNT) {
        activeFrame = FRAME_IMAGES; autoScrollKey++
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Background)
            .focusRequester(focusRequester).focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false

                // Interactive frames (have internal focus/key handling) — pass all keys through
                if (activeFrame in INTERACTIVE_FRAMES) return@onKeyEvent false

                // AI frame: custom mapping
                if (activeFrame == FRAME_AI) {
                    return@onKeyEvent when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT  -> { navigateFrame(-1); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { navigateFrame(1); true }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { activeFrame = FRAME_IMAGES; autoScrollKey++; true }
                        else -> { aiOkTrigger++; true }
                    }
                }

                // Passive frames (Images, Calendar, Habits) — outer handler manages all nav
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT  -> { navigateFrame(-1); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { navigateFrame(1); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (activeFrame == FRAME_IMAGES) { activeFrame = FRAME_AI; autoScrollKey++ }
                        true // consume on all passive frames — no accidental AI jump from calendar/habits
                    }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> when {
                        activeFrame >= AUTO_FRAME_COUNT -> { activeFrame = FRAME_IMAGES; autoScrollKey++; true }
                        else -> false
                    }
                    else -> false
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
                // Content fills entire screen
                Box(modifier = Modifier.fillMaxSize()) {
                    Crossfade(targetState = activeFrame, animationSpec = tween(800), label = "frame_crossfade") { index ->
                        when (index) {
                            FRAME_IMAGES -> ImageryFrame(images = state.images, serverBaseUrl = state.serverBaseUrl, imageLoader = imageLoader)
                            FRAME_TODO -> TodoFrame(
                                items = state.todos, onToggle = onToggleTodo,
                                onNavigateLeft = { navigateFrame(-1) },
                                onNavigateRight = { navigateFrame(1) },
                                onBack = { activeFrame = FRAME_IMAGES; autoScrollKey++ }
                            )
                            FRAME_HABITS -> HabitFrame(habits = state.habits)
                            FRAME_CALENDAR -> CalendarFrame(events = state.events)
                            FRAME_VIDEOS -> VideoFrame(
                                videos = state.videos, serverBaseUrl = state.serverBaseUrl,
                                onNavigateLeft = { navigateFrame(-1) },
                                onNavigateRight = { navigateFrame(1) },
                                onBack = { activeFrame = FRAME_IMAGES; autoScrollKey++ }
                            )
                            FRAME_MUSIC -> MusicFrame(
                                tracks = state.music, serverBaseUrl = state.serverBaseUrl,
                                onNavigateLeft = { navigateFrame(-1) },
                                onNavigateRight = { navigateFrame(1) },
                                onBack = { activeFrame = FRAME_IMAGES; autoScrollKey++ }
                            )
                            FRAME_PRESENT -> {
                                val pres = state.presentation
                                if (pres != null) {
                                    PresentFrame(
                                        presentation = pres,
                                        serverBaseUrl = state.serverBaseUrl,
                                        imageLoader = imageLoader,
                                        onNavigateLeft = { navigateFrame(-1) },
                                        onNavigateRight = { navigateFrame(1) },
                                        onPageChange = onPresentPage,
                                        onClose = onPresentClose,
                                        onBack = { activeFrame = FRAME_IMAGES; autoScrollKey++ }
                                    )
                                }
                            }
                            FRAME_AI -> ChatFrame(
                                state = state,
                                okTrigger = aiOkTrigger,
                                onToggleTodo = onToggleTodo,
                                onAddTodo = onAddTodo,
                                onToggleHabit = onToggleHabit,
                                onSwitchFrame = { frame -> activeFrame = frame.coerceIn(0, TOTAL_FRAMES - 1); autoScrollKey++ },
                                onClearAiQuery = onClearAiQuery,
                                onMusicControl = { action ->
                                    // Send music command via WebSocket for MusicFrame to handle
                                    // For now, just switch to music frame on play
                                    if (action == "play") { activeFrame = FRAME_MUSIC; autoScrollKey++ }
                                }
                            )
                        }
                    }

                    // Pomodoro overlay
                    state.pomodoro?.let { pomo ->
                        if (pomo.running || pomo.remaining != pomo.duration) {
                            PomodoroOverlay(pomodoro = pomo, modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 24.dp))
                        }
                    }

                    // Alarm overlay
                    if (showAlarm) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Background.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "\u23F0", style = DashboardTypography.displayLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(text = alarmTitle, style = DashboardTypography.headlineLarge.copy(color = PrimaryContainer))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "starts at $alarmTime", style = DashboardTypography.headlineMedium.copy(color = OnSurfaceVariant))
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(text = "5 minutes", style = DashboardTypography.titleLarge.copy(color = Amber))
                            }
                        }
                    }
                }

                // StatusBar overlay (top)
                StatusBar(
                    connected = state.connected,
                    nowPlaying = null,
                    weatherEmoji = state.weatherEmoji,
                    weatherTemp = state.weatherTemp,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Frame indicator overlay (bottom)
                FrameIndicator(count = TOTAL_FRAMES, current = activeFrame, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 16.dp))
        }
    }
}
