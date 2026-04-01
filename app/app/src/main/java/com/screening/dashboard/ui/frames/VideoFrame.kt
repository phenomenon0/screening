package com.screening.dashboard.ui.frames

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.screening.shared.model.VideoInfo
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ControlState {
    HIDDEN,     // Clean viewing — LEFT/RIGHT navigate frames
    SHOWN,      // Controls visible — LEFT/RIGHT seek, UP playlist, DOWN settings
    PLAYLIST,   // Playlist panel — UP/DOWN select, CENTER plays
    SETTINGS    // Settings panel — UP/DOWN select option, LEFT/RIGHT change value
}

private data class AudioTrackInfo(val index: Int, val groupIndex: Int, val trackIndex: Int, val label: String, val selected: Boolean)
private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val REPEAT_OPTIONS = listOf(Player.REPEAT_MODE_ALL to "Repeat All", Player.REPEAT_MODE_ONE to "Repeat One", Player.REPEAT_MODE_OFF to "No Repeat")

@Composable
fun VideoFrame(
    videos: List<VideoInfo>,
    serverBaseUrl: String,
    onNavigateLeft: () -> Unit,
    onNavigateRight: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("No videos", style = DashboardTypography.titleLarge.copy(color = TextDim))
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ALL }
    }

    // Pause on lifecycle stop (TV power off), resume on start
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                androidx.lifecycle.Lifecycle.Event.ON_START -> {} // user resumes manually
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> exoPlayer.release()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlState by remember { mutableStateOf(ControlState.HIDDEN) }
    var selectedPlaylistIndex by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val playlistScrollState = rememberLazyListState()

    // Settings state
    var settingsIndex by remember { mutableIntStateOf(0) } // 0=audio, 1=speed, 2=repeat
    var audioTracks by remember { mutableStateOf<List<AudioTrackInfo>>(emptyList()) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_ALL) }

    // Refresh audio tracks when entering settings
    fun refreshAudioTracks() {
        val tracks = mutableListOf<AudioTrackInfo>()
        val groups = exoPlayer.currentTracks.groups
        groups.forEachIndexed { gi, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (ti in 0 until group.length) {
                    val format = group.getTrackFormat(ti)
                    val label = buildString {
                        append(format.language?.uppercase() ?: "Track ${tracks.size + 1}")
                        if (format.channelCount > 0) append(" (${format.channelCount}ch)")
                        if (format.label != null) append(" - ${format.label}")
                    }
                    tracks.add(AudioTrackInfo(tracks.size, gi, ti, label, group.isTrackSelected(ti)))
                }
            }
        }
        audioTracks = tracks
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = exoPlayer.currentMediaItemIndex
                if (controlState != ControlState.PLAYLIST) selectedPlaylistIndex = exoPlayer.currentMediaItemIndex
                errorMessage = null
            }
            override fun onPlayerError(error: PlaybackException) {
                errorMessage = when {
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network error"
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> "Cannot play this format"
                    else -> "Playback error"
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener); exoPlayer.stop(); exoPlayer.release() }
    }

    LaunchedEffect(videos, serverBaseUrl) {
        exoPlayer.clearMediaItems()
        videos.forEach { video ->
            exoPlayer.addMediaItem(MediaItem.Builder().setUri("$serverBaseUrl${video.url}").setMediaId(video.filename).build())
        }
        exoPlayer.prepare(); exoPlayer.playWhenReady = true
    }

    LaunchedEffect(isPlaying, controlState) {
        while (isPlaying && controlState != ControlState.HIDDEN) {
            positionMs = exoPlayer.currentPosition; durationMs = exoPlayer.duration.coerceAtLeast(1); delay(500)
        }
    }

    LaunchedEffect(controlState) {
        if (controlState == ControlState.SHOWN && isPlaying) { delay(6000); if (controlState == ControlState.SHOWN) controlState = ControlState.HIDDEN }
        if (controlState == ControlState.SETTINGS) refreshAudioTracks()
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val currentVideo = videos.getOrNull(currentIndex)
    val showOverlay = controlState != ControlState.HIDDEN || !isPlaying || errorMessage != null

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focusRequester).focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                // Media keys in all states
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_CHANNEL_UP -> { if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem(); return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPreviousMediaItem(); return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play(); return@onKeyEvent true }
                }
                if (errorMessage != null) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { errorMessage = null; exoPlayer.prepare(); exoPlayer.play(); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { errorMessage = null; if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem(); exoPlayer.prepare(); exoPlayer.play(); true }
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { errorMessage = null; onBack(); true }
                        else -> true
                    }
                } else when (controlState) {
                    ControlState.HIDDEN -> when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { onNavigateLeft(); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { onNavigateRight(); true }
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onBack(); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { controlState = ControlState.SHOWN; true }
                        else -> { controlState = ControlState.SHOWN; true }
                    }
                    ControlState.SHOWN -> when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play(); true }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { exoPlayer.seekBack(); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { exoPlayer.seekForward(); true }
                        KeyEvent.KEYCODE_DPAD_UP -> { selectedPlaylistIndex = currentIndex; controlState = ControlState.PLAYLIST; true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { settingsIndex = 0; controlState = ControlState.SETTINGS; true }
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { controlState = ControlState.HIDDEN; true }
                        else -> true
                    }
                    ControlState.PLAYLIST -> when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> { if (selectedPlaylistIndex > 0) { selectedPlaylistIndex--; coroutineScope.launch { playlistScrollState.animateScrollToItem(selectedPlaylistIndex) } }; true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { if (selectedPlaylistIndex < videos.size - 1) { selectedPlaylistIndex++; coroutineScope.launch { playlistScrollState.animateScrollToItem(selectedPlaylistIndex) } }; true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { exoPlayer.seekTo(selectedPlaylistIndex, 0); controlState = ControlState.SHOWN; true }
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DPAD_RIGHT -> { controlState = ControlState.SHOWN; true }
                        KeyEvent.KEYCODE_DPAD_LEFT -> true
                        else -> true
                    }
                    ControlState.SETTINGS -> {
                        val settingsCount = 3 // audio, speed, repeat
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> { if (settingsIndex > 0) settingsIndex--; true }
                            KeyEvent.KEYCODE_DPAD_DOWN -> { if (settingsIndex < settingsCount - 1) settingsIndex++; true }
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                val delta = if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                                when (settingsIndex) {
                                    0 -> { // Audio track
                                        if (audioTracks.size > 1) {
                                            val currentIdx = audioTracks.indexOfFirst { it.selected }.coerceAtLeast(0)
                                            val nextIdx = (currentIdx + delta + audioTracks.size) % audioTracks.size
                                            val track = audioTracks[nextIdx]
                                            val groups = exoPlayer.currentTracks.groups
                                            val group = groups[track.groupIndex]
                                            val override = TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                                .setOverrideForType(override).build()
                                            refreshAudioTracks()
                                        }
                                    }
                                    1 -> { // Speed
                                        val currentIdx = SPEED_OPTIONS.indexOf(currentSpeed).coerceAtLeast(0)
                                        val nextIdx = (currentIdx + delta).coerceIn(0, SPEED_OPTIONS.size - 1)
                                        currentSpeed = SPEED_OPTIONS[nextIdx]
                                        exoPlayer.playbackParameters = PlaybackParameters(currentSpeed)
                                    }
                                    2 -> { // Repeat
                                        val currentIdx = REPEAT_OPTIONS.indexOfFirst { it.first == repeatMode }.coerceAtLeast(0)
                                        val nextIdx = (currentIdx + delta + REPEAT_OPTIONS.size) % REPEAT_OPTIONS.size
                                        repeatMode = REPEAT_OPTIONS[nextIdx].first
                                        exoPlayer.repeatMode = repeatMode
                                    }
                                }
                                true
                            }
                            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                controlState = ControlState.SHOWN; true
                            }
                            else -> true
                        }
                    }
                }
            }
    ) {
        // Video surface
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = false } }, modifier = Modifier.fillMaxSize())

        // Controls overlay
        AnimatedVisibility(visible = showOverlay, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top: title
                Box(modifier = Modifier.fillMaxWidth().height(100.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(text = cleanName(currentVideo?.filename ?: ""), style = DashboardTypography.titleLarge)
                        if (videos.size > 1) Text(text = "${currentIndex + 1} of ${videos.size}", style = DashboardTypography.bodyMedium.copy(color = TextSecondary))
                    }
                }

                // Center: play/pause or error
                if (!isPlaying && errorMessage == null) {
                    Box(modifier = Modifier.align(Alignment.Center).size(80.dp).clip(RoundedCornerShape(40.dp)).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                        Text("\u25B6", fontSize = 36.sp, color = Color.White)
                    }
                }
                if (errorMessage != null) {
                    Box(modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(DarkCard.copy(alpha = 0.95f)).padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = errorMessage ?: "", style = DashboardTypography.titleLarge.copy(color = Color(0xFFFF5252)))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = "OK to retry  |  \u25B6 Skip  |  BACK to exit", style = DashboardTypography.bodyMedium.copy(color = TextSecondary))
                        }
                    }
                }

                // Bottom: progress + hints
                Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))).padding(24.dp)) {
                    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).background(AccentCyan))
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Status badges
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = "${formatTime(positionMs)} / ${formatTime(durationMs)}", style = DashboardTypography.bodyMedium.copy(color = TextSecondary))
                            if (currentSpeed != 1.0f) Text(text = "${currentSpeed}x", style = DashboardTypography.bodyMedium.copy(color = AccentCyan))
                            if (repeatMode == Player.REPEAT_MODE_ONE) Text(text = "1x", style = DashboardTypography.bodyMedium.copy(color = AccentAmber))
                            if (repeatMode == Player.REPEAT_MODE_OFF) Text(text = "No repeat", style = DashboardTypography.bodyMedium.copy(color = TextDim))
                        }
                        Text(text = when (controlState) {
                            ControlState.SHOWN -> "\u25C0 -10s  \u25B6 +10s  \u25CF Play  \u25B2 Playlist  \u25BC Settings"
                            ControlState.PLAYLIST -> "\u25B2\u25BC Select  \u25CF Play  BACK Close"
                            ControlState.SETTINGS -> "\u25B2\u25BC Option  \u25C0\u25B6 Change  BACK Close"
                            ControlState.HIDDEN -> "\u25CF Show controls  \u25C0\u25B6 Navigate"
                        }, style = DashboardTypography.labelSmall.copy(color = TextDim))
                    }
                }
            }
        }

        // Playlist panel (slides from right)
        AnimatedVisibility(visible = controlState == ControlState.PLAYLIST, enter = slideInHorizontally(initialOffsetX = { it }), exit = slideOutHorizontally(targetOffsetX = { it }), modifier = Modifier.align(Alignment.TopEnd)) {
            Box(modifier = Modifier.width(350.dp).fillMaxHeight().background(DarkBackground.copy(alpha = 0.95f)).padding(16.dp)) {
                Column {
                    Text(text = "Playlist", style = DashboardTypography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp))
                    LazyColumn(state = playlistScrollState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(videos) { index, video ->
                            val isCurrent = index == currentIndex; val isSelected = index == selectedPlaylistIndex
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(when { isSelected -> AccentCyan.copy(alpha = 0.25f); isCurrent -> AccentBlue; else -> DarkCard }).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(text = when { isCurrent -> "\u25B6"; isSelected -> "\u203A"; else -> "${index + 1}" },
                                    style = DashboardTypography.bodyMedium.copy(color = if (isSelected || isCurrent) AccentCyan else TextDim), modifier = Modifier.width(32.dp))
                                Text(text = cleanName(video.filename), style = DashboardTypography.bodyMedium.copy(color = if (isSelected || isCurrent) TextPrimary else TextSecondary), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Settings panel (slides from left)
        AnimatedVisibility(visible = controlState == ControlState.SETTINGS, enter = slideInHorizontally(initialOffsetX = { -it }), exit = slideOutHorizontally(targetOffsetX = { -it }), modifier = Modifier.align(Alignment.TopStart)) {
            Box(modifier = Modifier.width(350.dp).fillMaxHeight().background(DarkBackground.copy(alpha = 0.95f)).padding(16.dp)) {
                Column {
                    Text(text = "Settings", style = DashboardTypography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))

                    // Audio track
                    SettingsRow(
                        label = "Audio",
                        value = audioTracks.firstOrNull { it.selected }?.label ?: if (audioTracks.isEmpty()) "Default" else audioTracks.first().label,
                        isSelected = settingsIndex == 0,
                        hasOptions = audioTracks.size > 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Speed
                    SettingsRow(
                        label = "Speed",
                        value = if (currentSpeed == 1.0f) "Normal" else "${currentSpeed}x",
                        isSelected = settingsIndex == 1,
                        hasOptions = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Repeat
                    SettingsRow(
                        label = "Repeat",
                        value = REPEAT_OPTIONS.first { it.first == repeatMode }.second,
                        isSelected = settingsIndex == 2,
                        hasOptions = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "\u25C0\u25B6 to change  \u25B2\u25BC to navigate  BACK to close",
                        style = DashboardTypography.labelSmall.copy(color = TextDim)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String, isSelected: Boolean, hasOptions: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentCyan.copy(alpha = 0.2f) else DarkCard)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = DashboardTypography.bodyLarge.copy(color = if (isSelected) AccentCyan else TextSecondary))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasOptions && isSelected) Text(text = "\u25C0", style = DashboardTypography.bodyMedium.copy(color = AccentCyan))
            Text(text = value, style = DashboardTypography.bodyLarge.copy(color = TextPrimary))
            if (hasOptions && isSelected) Text(text = "\u25B6", style = DashboardTypography.bodyMedium.copy(color = AccentCyan))
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000; val hrs = totalSec / 3600; val mins = (totalSec % 3600) / 60; val secs = totalSec % 60
    return if (hrs > 0) "$hrs:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}" else "$mins:${secs.toString().padStart(2, '0')}"
}

private fun cleanName(filename: String): String = filename.substringBeforeLast(".").replace("_", " ").replace("-", " ").trim()
