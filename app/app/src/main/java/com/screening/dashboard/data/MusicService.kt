package com.screening.dashboard.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    companion object {
        private const val CHANNEL_ID = "screening_music"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context, baseUrl: String, tracks: List<com.screening.shared.model.MusicTrack>) {
            val intent = Intent(context, MusicService::class.java).apply {
                putExtra("base_url", baseUrl)
                putStringArrayListExtra("urls", ArrayList(tracks.map { "$baseUrl${it.url}" }))
                putStringArrayListExtra("names", ArrayList(tracks.map { it.filename }))
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MusicService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            shuffleModeEnabled = true
        }
        mediaSession = MediaSession.Builder(this, player!!).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val urls = intent?.getStringArrayListExtra("urls") ?: return START_NOT_STICKY

        if (urls.isNotEmpty()) {
            player?.clearMediaItems()
            urls.forEach { url ->
                player?.addMediaItem(MediaItem.fromUri(url))
            }
            player?.prepare()
            player?.playWhenReady = true

            val name = intent.getStringArrayListExtra("names")?.firstOrNull() ?: "Music"
            startForeground(NOTIFICATION_ID, buildNotification(name))
        }

        return START_NOT_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screening Music",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(trackName: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screening")
            .setContentText("Playing: $trackName")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }
}
