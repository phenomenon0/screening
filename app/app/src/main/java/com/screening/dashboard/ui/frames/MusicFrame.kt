package com.screening.dashboard.ui.frames

import android.view.KeyEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Text
import com.screening.shared.model.MusicTrack
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun MusicFrame(
    tracks: List<MusicTrack>,
    serverBaseUrl: String,
    onNavigateLeft: () -> Unit,
    onNavigateRight: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (tracks.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
            Text("No music", style = DashboardTypography.titleLarge.copy(color = TextDim))
        }
        return
    }

    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            shuffleModeEnabled = false
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var showControls by remember { mutableStateOf(true) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = exoPlayer.currentMediaItemIndex
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener); exoPlayer.release() }
    }

    LaunchedEffect(tracks, serverBaseUrl) {
        exoPlayer.clearMediaItems()
        tracks.forEach { track ->
            exoPlayer.addMediaItem(MediaItem.Builder().setUri("$serverBaseUrl${track.url}").setMediaId(track.filename).build())
        }
        exoPlayer.prepare(); exoPlayer.playWhenReady = false
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(1)
            delay(200)
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls && isPlaying) { delay(5000); showControls = false }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val currentTrack = tracks.getOrNull(currentIndex)
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    Box(
        modifier = modifier.fillMaxSize().background(DarkBackground)
            .focusRequester(focusRequester).focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                showControls = true
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (showControls && isPlaying) { exoPlayer.seekBack(); true }
                        else { onNavigateLeft(); true }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (showControls && isPlaying) { exoPlayer.seekForward(); true }
                        else { onNavigateRight(); true }
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play(); true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem(); true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPreviousMediaItem(); true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play(); true }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onBack(); true }
                    else -> true
                }
            }
    ) {
        // Main layout matching wireframe
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Card: album art + track info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: waveform visualization
                Box(
                    modifier = Modifier
                        .weight(0.45f)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0A0A1A))
                ) {
                    WaveformVisualizer(isPlaying = isPlaying, progress = progress)
                    if (!isPlaying) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("\u25B6", fontSize = 24.sp, color = AccentCyan)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Right: track info
                Column(
                    modifier = Modifier.weight(0.55f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Artist (extracted from filename or shown as-is)
                    val (artist, title) = parseTrackName(currentTrack?.filename ?: "Unknown")

                    Text(
                        text = "..$artist",
                        style = DashboardTypography.headlineMedium.copy(color = TextSecondary),
                        maxLines = 1
                    )

                    // Dot progress bar
                    DotProgressBar(progress = progress, color = TextDim)

                    Spacer(modifier = Modifier.height(4.dp))

                    // Track title with highlight background
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentAmber.copy(alpha = 0.8f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = title,
                            style = DashboardTypography.headlineMedium.copy(color = DarkBackground),
                            maxLines = 1
                        )
                    }

                    // Second dot progress bar
                    DotProgressBar(progress = progress, color = TextDim)

                    // Track counter
                    if (tracks.size > 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${currentIndex + 1} / ${tracks.size}",
                            style = DashboardTypography.bodyMedium.copy(color = TextDim)
                        )
                    }
                }

                // Top-right status indicators
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Box(modifier = Modifier.width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (isPlaying) AccentGreen else AccentRed))
                    Box(modifier = Modifier.width(18.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (isPlaying) AccentGreen.copy(alpha = 0.6f) else TextDim))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Seek bar
            Box(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Track
                Box(
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.15f))
                )
                // Progress
                Box(
                    modifier = Modifier.fillMaxWidth(progress).height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(AccentCyan)
                )
                // Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .wrapContentWidth(Alignment.End)
                        .size(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White)
                )
            }

            // Time + controls hint
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                    style = DashboardTypography.bodyMedium.copy(color = TextSecondary)
                )
                if (showControls) {
                    Text(
                        text = "● Play/Pause  ▲ Next  ▼ Prev  ◀▶ Seek",
                        style = DashboardTypography.labelSmall.copy(color = TextDim)
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveformVisualizer(isPlaying: Boolean, progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 6.28f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "wavePhase"
    )

    val barHeights = remember { List(24) { Random.nextFloat() * 0.5f + 0.3f } }

    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val w = size.width
        val h = size.height
        val barCount = barHeights.size
        val barWidth = w / (barCount * 2f)
        val gap = barWidth

        for (i in 0 until barCount) {
            val baseHeight = barHeights[i]
            val animatedHeight = if (isPlaying) {
                baseHeight * (0.6f + 0.4f * sin(phase + i * 0.5f).coerceIn(0f, 1f))
            } else {
                baseHeight * 0.3f
            }
            val barH = h * animatedHeight
            val x = i * (barWidth + gap)

            // Gradient-like: brighter bars near progress position
            val distFromProgress = kotlin.math.abs(i.toFloat() / barCount - progress)
            val alpha = if (i.toFloat() / barCount <= progress) {
                0.9f - distFromProgress * 0.3f
            } else {
                0.3f
            }

            drawRoundRect(
                color = AccentCyan.copy(alpha = alpha.coerceIn(0.15f, 0.9f)),
                topLeft = Offset(x, h - barH),
                size = androidx.compose.ui.geometry.Size(barWidth, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
            )
        }
    }
}

@Composable
private fun DotProgressBar(progress: Float, color: Color) {
    val dotCount = 20
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until dotCount) {
            val dotProgress = i.toFloat() / dotCount
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (dotProgress <= progress) AccentCyan.copy(alpha = 0.8f)
                        else color.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

private fun parseTrackName(filename: String): Pair<String, String> {
    val clean = filename.substringBeforeLast(".").replace("_", " ").replace("-", " ").trim()
    // Try "Artist - Title" pattern
    val parts = clean.split("  ", " - ", " – ")
    return if (parts.size >= 2) {
        parts[0].trim() to parts.drop(1).joinToString(" ").trim()
    } else {
        "Unknown" to clean
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000; val hrs = totalSec / 3600; val mins = (totalSec % 3600) / 60; val secs = totalSec % 60
    return if (hrs > 0) "$hrs:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}" else "$mins:${secs.toString().padStart(2, '0')}"
}
