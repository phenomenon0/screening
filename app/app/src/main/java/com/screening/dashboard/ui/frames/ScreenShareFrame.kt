package com.screening.dashboard.ui.frames

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun ScreenShareFrame(
    streamUrl: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var showHint by remember { mutableStateOf(false) }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(streamUrl) { exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl)); exoPlayer.prepare(); exoPlayer.playWhenReady = true }
    LaunchedEffect(streamUrl) { delay(3000); showHint = true; delay(5000); showHint = false }
    DisposableEffect(Unit) { onDispose { exoPlayer.stop(); exoPlayer.release() } }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focusRequester).focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onExit(); true }
                    else -> false
                }
            }
    ) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = false } }, modifier = Modifier.fillMaxSize())
        Text(text = "Screen Share", style = DashboardTypography.labelSmall.copy(color = AccentCyan), modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
        AnimatedVisibility(visible = showHint, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = "Press BACK to exit", style = DashboardTypography.bodyMedium.copy(color = TextSecondary))
            }
        }
    }
}
