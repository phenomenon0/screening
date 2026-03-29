package com.screening.dashboard.ui

import android.view.KeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import com.screening.dashboard.model.DashboardState
import com.screening.dashboard.ui.components.FrameIndicator
import com.screening.dashboard.ui.components.StatusBar
import com.screening.dashboard.ui.frames.*
import com.screening.dashboard.ui.theme.DarkBackground
import com.screening.dashboard.ui.theme.DashboardTheme
import kotlinx.coroutines.delay

// Auto-rotation: Images(8min) → Todo(2min) → Calendar(1min)
// Video is manual only — navigate to it with D-pad
private const val FRAME_IMAGES = 0
private const val FRAME_TODO = 1
private const val FRAME_CALENDAR = 2
private const val FRAME_VIDEOS = 3  // manual only, not in auto-rotation
private const val AUTO_FRAME_COUNT = 3  // only first 3 auto-rotate

private fun frameDurationMs(frame: Int): Long = when (frame) {
    FRAME_IMAGES -> 8 * 60_000L    // 8 minutes
    FRAME_TODO -> 2 * 60_000L      // 2 minutes
    FRAME_CALENDAR -> 60_000L      // 1 minute
    else -> 60_000L
}

@Composable
fun DashboardScreen(
    state: DashboardState,
    imageLoader: ImageLoader,
    onToggleTodo: (String) -> Unit,
    onScreenShareStop: () -> Unit = {}
) {
    DashboardTheme {
        if (state.screenShareUrl != null) {
            ScreenShareFrame(streamUrl = state.screenShareUrl)
            return@DashboardTheme
        }

        var activeFrame by remember { mutableIntStateOf(FRAME_IMAGES) }
        val focusRequester = remember { FocusRequester() }
        val hasVideos = state.videos.isNotEmpty()

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

        val totalFrames = if (hasVideos) AUTO_FRAME_COUNT + 1 else AUTO_FRAME_COUNT

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
                            // Skip video frame if no videos
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
                        KeyEvent.KEYCODE_DPAD_DOWN -> true
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
                        FRAME_CALENDAR -> CalendarFrame(events = state.events)
                        FRAME_VIDEOS -> VideoFrame(
                            videos = state.videos,
                            serverBaseUrl = state.serverBaseUrl
                        )
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
}
