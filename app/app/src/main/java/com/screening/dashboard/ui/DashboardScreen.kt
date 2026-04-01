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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.screening.dashboard.ui.components.FrameIndicator
import com.screening.dashboard.ui.components.NavRail
import com.screening.dashboard.ui.components.PomodoroOverlay
import com.screening.dashboard.ui.components.StatusBar
import com.screening.dashboard.ui.frames.*
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Text
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.delay

private const val FRAME_IMAGES = 0
private const val FRAME_TODO = 1
private const val FRAME_HABITS = 2
private const val FRAME_CALENDAR = 3
private const val FRAME_VIDEOS = 4
private const val FRAME_MUSIC = 5
private const val AUTO_FRAME_COUNT = 4
private const val TOTAL_FRAMES = 6

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
    onScreenShareStop: () -> Unit = {},
    onClearForceFrame: () -> Unit = {}
) {
    val screenUrl = state.screenShareUrl
    if (screenUrl != null) {
        ScreenShareFrame(streamUrl = screenUrl, onExit = onScreenShareStop)
        BackHandler { onScreenShareStop() }
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var activeFrame by remember { mutableIntStateOf(FRAME_IMAGES) }
    var showQR by remember { mutableStateOf(false) }
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
        if (state.alarmTitle != null) {
            alarmTitle = state.alarmTitle!!
            alarmTime = state.alarmTime ?: ""
            showAlarm = true
            try { alarmPlayer.start() } catch (_: Exception) {}
            delay(20_000)
            showAlarm = false
            try { alarmPlayer.pause(); alarmPlayer.seekTo(0) } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) { onDispose { try { alarmPlayer.release() } catch (_: Exception) {} } }

    var autoScrollKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(autoScrollKey, activeFrame) {
        if (activeFrame < AUTO_FRAME_COUNT) { delay(frameDurationMs(activeFrame)); activeFrame = (activeFrame + 1) % AUTO_FRAME_COUNT }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun navigateFrame(delta: Int) {
        var next = (activeFrame + delta + TOTAL_FRAMES) % TOTAL_FRAMES
        if (next == FRAME_VIDEOS && !hasVideos) next = (next + delta + TOTAL_FRAMES) % TOTAL_FRAMES
        if (next == FRAME_MUSIC && !hasMusic) next = (next + delta + TOTAL_FRAMES) % TOTAL_FRAMES
        activeFrame = next; autoScrollKey++
    }

    BackHandler(enabled = activeFrame >= AUTO_FRAME_COUNT || showQR) {
        when {
            showQR -> showQR = false
            activeFrame >= AUTO_FRAME_COUNT -> { activeFrame = FRAME_IMAGES; autoScrollKey++ }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Background)
            .focusRequester(focusRequester).focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                if (activeFrame == FRAME_VIDEOS || activeFrame == FRAME_TODO || activeFrame == FRAME_MUSIC) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { navigateFrame(-1); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { navigateFrame(1); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { true }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { showQR = !showQR; true }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> when {
                        showQR -> { showQR = false; true }
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
                        }
                    }

                    // Pomodoro overlay
                    state.pomodoro?.let { pomo ->
                        if (pomo.running || pomo.remaining != pomo.duration) {
                            PomodoroOverlay(pomodoro = pomo, modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 24.dp))
                        }
                    }

                    // QR overlay
                    if (showQR) {
                        Box(modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(SurfaceContainerHigh.copy(alpha = 0.95f)).padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Scan to connect", style = DashboardTypography.headlineMedium)
                                Spacer(modifier = Modifier.height(16.dp))
                                AsyncImage(model = ImageRequest.Builder(LocalPlatformContext.current).data("${state.serverBaseUrl}/qr.png").build(), imageLoader = imageLoader, contentDescription = "QR Code", modifier = Modifier.size(200.dp), contentScale = ContentScale.Fit)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = state.serverBaseUrl, style = DashboardTypography.bodyMedium.copy(color = PrimaryContainer))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Press Up/Down to dismiss", style = DashboardTypography.labelSmall)
                            }
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
                    nowPlaying = state.music.firstOrNull()?.filename,
                    weatherEmoji = state.weatherEmoji,
                    weatherTemp = state.weatherTemp,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Frame indicator overlay (bottom)
                FrameIndicator(count = TOTAL_FRAMES, current = activeFrame, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 16.dp))
        }
    }
}
