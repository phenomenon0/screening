package com.screening.dashboard.ui.frames

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.screening.dashboard.model.VideoInfo
import com.screening.dashboard.ui.theme.*

@Composable
fun VideoFrame(
    videos: List<VideoInfo>,
    serverBaseUrl: String,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No videos",
                style = DashboardTypography.titleLarge.copy(color = TextDim)
            )
        }
        return
    }

    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    // Build playlist from video list
    LaunchedEffect(videos, serverBaseUrl) {
        exoPlayer.clearMediaItems()
        videos.forEach { video ->
            val url = "$serverBaseUrl${video.url}"
            exoPlayer.addMediaItem(MediaItem.fromUri(url))
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // clean display, no controls overlay
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
