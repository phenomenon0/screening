package com.screening.dashboard.ui.frames

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.screening.shared.model.VideoInfo
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun VideoFrame(
    videos: List<VideoInfo>,
    serverBaseUrl: String,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("No videos", style = DashboardTypography.titleLarge.copy(color = TextDim))
        }
        return
    }

    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    // Track player state
    var isPlaying by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var showPlaylist by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // Listen to player events
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = exoPlayer.currentMediaItemIndex
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Build playlist
    LaunchedEffect(videos, serverBaseUrl) {
        exoPlayer.clearMediaItems()
        videos.forEach { video ->
            val url = "$serverBaseUrl${video.url}"
            val item = MediaItem.Builder()
                .setUri(url)
                .setMediaId(video.filename)
                .build()
            exoPlayer.addMediaItem(item)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Poll position only while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(1)
            delay(500)
        }
    }

    // Auto-hide controls after 4 seconds
    LaunchedEffect(showControls) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Grab focus
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val currentVideo = videos.getOrNull(currentIndex)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                showControls = true // any key shows controls
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        exoPlayer.seekForward() // 10s forward
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        exoPlayer.seekBack() // 10s back
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (showPlaylist) {
                            // Navigate playlist
                            false
                        } else {
                            showPlaylist = !showPlaylist
                            true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        showPlaylist = false
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPreviousMediaItem()
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // we build our own overlay
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Controls overlay
        AnimatedVisibility(
            visible = showControls || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top gradient + title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = cleanName(currentVideo?.filename ?: ""),
                            style = DashboardTypography.titleLarge
                        )
                        Text(
                            text = "${currentIndex + 1} of ${videos.size}",
                            style = DashboardTypography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                }

                // Center play/pause indicator
                if (!isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(80.dp)
                            .clip(RoundedCornerShape(40.dp))
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▶", fontSize = 36.sp, color = Color.White)
                    }
                }

                // Bottom: progress bar + time
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(24.dp)
                ) {
                    // Progress bar
                    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(AccentCyan)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Time + controls hint
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                            style = DashboardTypography.bodyMedium.copy(color = TextSecondary)
                        )
                        Text(
                            text = "◀ -10s  ▶ +10s  ● Play/Pause  ▲ Playlist",
                            style = DashboardTypography.labelSmall.copy(color = TextDim)
                        )
                    }
                }
            }
        }

        // Playlist panel (slides from top when UP is pressed)
        AnimatedVisibility(
            visible = showPlaylist,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Box(
                modifier = Modifier
                    .width(350.dp)
                    .fillMaxHeight()
                    .background(DarkBackground.copy(alpha = 0.95f))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "Playlist",
                        style = DashboardTypography.headlineMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(videos) { index, video ->
                            val isCurrent = index == currentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) AccentBlue else DarkCard)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isCurrent) "▶" else "${index + 1}",
                                    style = DashboardTypography.bodyMedium.copy(
                                        color = if (isCurrent) AccentCyan else TextDim
                                    ),
                                    modifier = Modifier.width(32.dp)
                                )
                                Text(
                                    text = cleanName(video.filename),
                                    style = DashboardTypography.bodyMedium.copy(
                                        color = if (isCurrent) TextPrimary else TextSecondary
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hrs > 0) {
        "$hrs:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    } else {
        "$mins:${secs.toString().padStart(2, '0')}"
    }
}

private fun cleanName(filename: String): String {
    return filename
        .substringBeforeLast(".")
        .replace("_", " ")
        .replace("-", " ")
        .trim()
}
