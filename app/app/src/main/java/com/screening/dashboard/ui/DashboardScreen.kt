package com.screening.dashboard.ui

import android.view.KeyEvent
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.screening.dashboard.ui.components.FrameIndicator
import com.screening.dashboard.ui.components.PomodoroOverlay
import com.screening.dashboard.ui.components.StatusBar
import com.screening.dashboard.ui.frames.*
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Text
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.delay

// Auto-rotation: Images(8min) → Todo(2min) → Habits(1min) → Calendar(1min)
// Video is manual only — navigate to it with D-pad
private const val FRAME_IMAGES = 0
private const val FRAME_TODO = 1
private const val FRAME_HABITS = 2
private const val FRAME_CALENDAR = 3
private const val FRAME_VIDEOS = 4  // manual only, not in auto-rotation
private const val FRAME_SCENE = 5   // manual only — 3D scene
private const val AUTO_FRAME_COUNT = 4  // first 4 auto-rotate

private fun frameDurationMs(frame: Int): Long = when (frame) {
    FRAME_IMAGES -> 8 * 60_000L    // 8 minutes
    FRAME_TODO -> 2 * 60_000L      // 2 minutes
    FRAME_HABITS -> 60_000L        // 1 minute
    FRAME_CALENDAR -> 60_000L      // 1 minute
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
        ScreenShareFrame(streamUrl = screenUrl)
        return
    }

    var activeFrame by remember { mutableIntStateOf(FRAME_IMAGES) }
        var showQR by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        val hasVideos = state.videos.isNotEmpty()

        // Handle remote frame change from web companion
        LaunchedEffect(state.forceFrame) {
            state.forceFrame?.let {
                activeFrame = it
                onClearForceFrame()
            }
        }

        // Auto-rotate only through images/todo/calendar
        var autoScrollKey by remember { mutableIntStateOf(0) }
        LaunchedEffect(autoScrollKey, activeFrame) {
            if (activeFrame < AUTO_FRAME_COUNT) {
                // In auto-rotation zone
                delay(frameDurationMs(activeFrame))
                activeFrame = (activeFrame + 1) % AUTO_FRAME_COUNT
            }
            // If on video frame, stay there until user navigates away
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        // Videos (frame 4) and Scene (frame 5) are both manual-only
        val totalFrames = FRAME_SCENE + 1 // always 6 — all frames accessible manually

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            var next = (activeFrame - 1 + totalFrames) % totalFrames
                            // Skip empty video frame on manual nav
                            if (next == FRAME_VIDEOS && !hasVideos) {
                                next = (next - 1 + totalFrames) % totalFrames
                            }
                            activeFrame = next
                            autoScrollKey++
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            var next = (activeFrame + 1) % totalFrames
                            if (next == FRAME_VIDEOS && !hasVideos) {
                                next = (next + 1) % totalFrames
                            }
                            activeFrame = next
                            autoScrollKey++
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> {
                            if (activeFrame == FRAME_TODO && state.todos.isNotEmpty()) {
                                onToggleTodo(state.todos.first().id)
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            showQR = !showQR // toggle QR overlay
                            true
                        }
                        else -> false
                    }
                }
        ) {
            StatusBar(
                connected = state.connected,
                nowPlaying = if (state.music.isNotEmpty()) state.music.firstOrNull()?.filename else null
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Crossfade(
                    targetState = activeFrame,
                    animationSpec = tween(800),
                    label = "frame_crossfade"
                ) { index ->
                    when (index) {
                        FRAME_IMAGES -> ImageryFrame(
                            images = state.images,
                            serverBaseUrl = state.serverBaseUrl,
                            imageLoader = imageLoader
                        )
                        FRAME_TODO -> TodoFrame(items = state.todos, onToggle = onToggleTodo)
                        FRAME_HABITS -> HabitFrame(habits = state.habits)
                        FRAME_CALENDAR -> CalendarFrame(events = state.events)
                        FRAME_VIDEOS -> VideoFrame(
                            videos = state.videos,
                            serverBaseUrl = state.serverBaseUrl
                        )
                        FRAME_SCENE -> SceneFrame(
                            serverBaseUrl = state.serverBaseUrl,
                            sceneId = null
                        )
                    }
                }

                // Pomodoro overlay — shows when timer is active
                state.pomodoro?.let { pomo ->
                    if (pomo.running || pomo.remaining != pomo.duration) {
                        PomodoroOverlay(
                            pomodoro = pomo,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 48.dp)
                        )
                    }
                }

                // QR code overlay
                if (showQR) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkCard.copy(alpha = 0.95f))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Scan to connect",
                                style = DashboardTypography.headlineMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            AsyncImage(
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data("${state.serverBaseUrl}/qr.png")
                                    .build(),
                                imageLoader = imageLoader,
                                contentDescription = "QR Code",
                                modifier = Modifier.size(200.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = state.serverBaseUrl,
                                style = DashboardTypography.bodyMedium.copy(color = AccentCyan)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Press Up/Down to dismiss",
                                style = DashboardTypography.labelSmall
                            )
                        }
                    }
                }
            }

            FrameIndicator(
                count = totalFrames,
                current = activeFrame,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
}
