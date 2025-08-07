package com.mediacontrols

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.facebook.react.bridge.ReactApplicationContext
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class MediaControlsService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val binder = LocalBinder()
    private var mediaController: MediaController? = null
    private var hasMetadata = false

    companion object {
        private const val NOTIFICATION_ID = 123
        private const val CHANNEL_ID = "media_controls_channel"
        var reactContext: ReactApplicationContext? = null
        var player: MediaControlsPlayer? = null
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaControlsService = this@MediaControlsService
    }

    override fun onBind(intent: Intent?): IBinder {
        return if (intent?.action == "androidx.media3.session.MediaSessionService") {
            super.onBind(intent)!!
        } else {
            binder
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        // Create media session
        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(MediaSessionCallback())
            .build()

        // Create MediaController for media controls
        setupMediaController()

        android.util.Log.d("MediaControlsService", "Service created with new player instance")
    }

    private fun setupMediaController() {
        mediaSession?.let { session ->
            val sessionToken = SessionToken(this, ComponentName(this, MediaControlsService::class.java))

            val controllerFuture = MediaController.Builder(this, sessionToken)
                .buildAsync()

            controllerFuture.addListener({
                try {
                    mediaController = controllerFuture.get()
                    mediaController?.addListener(player!!.getListener())

                    // Create and show media notification
                    createMediaNotification()
                } catch (e: Exception) {
                    android.util.Log.e("MediaControlsService", "Failed to create MediaController", e)
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(this))
        }
    }

    private fun createMediaNotification() {
        val player = player ?: return

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(player.mediaMetadata.title ?: "Unknown Title")
            .setContentText(player.mediaMetadata.artist ?: "Unknown Artist")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Sammle nur aktivierte Actions
        val actions = mutableListOf<NotificationCompat.Action>()
        val compactViewIndices = mutableListOf<Int>()

        // Previous Button (wenn enabled)
        if (player.isControlEnabled("previous")) {
            actions.add(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    createPendingIntent("previous")
                )
            )
        }

        // Play/Pause Button (wenn play oder pause enabled)
        if (player.isControlEnabled("play") || player.isControlEnabled("pause")) {
            val playPauseIndex = actions.size
            compactViewIndices.add(playPauseIndex) // Play/Pause immer in Compact View

            actions.add(
                NotificationCompat.Action(
                    if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (player.isPlaying) "Pause" else "Play",
                    createPendingIntent("play_pause")
                )
            )
        }

        // Next Button (wenn enabled)
        if (player.isControlEnabled("next")) {
            val nextIndex = actions.size
            if (compactViewIndices.size < 3) {
                compactViewIndices.add(nextIndex)
            }

            actions.add(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    createPendingIntent("next")
                )
            )
        }

        // Seek Backward (wenn seek enabled)
        if (player.isControlEnabled("seek")) {
            actions.add(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_rew,
                    "Rewind",
                    createPendingIntent("rewind")
                )
            )
        }

        // Seek Forward (wenn seek enabled)
        if (player.isControlEnabled("seek")) {
            actions.add(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_ff,
                    "Fast Forward",
                    createPendingIntent("fast_forward")
                )
            )
        }

        // Stop Button (wenn enabled)
        if (player.isControlEnabled("stop")) {
            actions.add(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    "Stop",
                    createPendingIntent("stop")
                )
            )
        }

        // Füge alle Actions zur Notification hinzu
        actions.forEach { action ->
            notificationBuilder.addAction(action)
        }

        // MediaStyle mit korrekten CompactView Indizes
        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(mediaSession!!)

        if (compactViewIndices.isNotEmpty()) {
            mediaStyle.setShowActionsInCompactView(*compactViewIndices.toIntArray())
        }

        notificationBuilder.setStyle(mediaStyle)

        val notification = notificationBuilder.build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createPendingIntent(action: String): android.app.PendingIntent {
        val intent = Intent(this, MediaControlsService::class.java).apply {
            putExtra("action", action)
        }
        return android.app.PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")

        action?.let { handleNotificationAction(it) }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleNotificationAction(action: String) {
        val module = reactContext?.getNativeModule(MediaControlsModule::class.java)
        val player = player

        when (action) {
            "play_pause" -> {
                player?.let { p ->
                    if (p.isPlaying && p.isControlEnabled("pause")) {
                        p.pause()
                        module?.sendEvent("pause", null)
                    } else if (!p.isPlaying && p.isControlEnabled("play")) {
                        p.play()
                        module?.sendEvent("play", null)
                    }
                    // Update notification nach State-Änderung
                    createMediaNotification()
                }
            }
            "next" -> {
                if (player?.isControlEnabled("next") == true) {
                    module?.sendEvent("skipToNext", null)
                }
            }
            "previous" -> {
                if (player?.isControlEnabled("previous") == true) {
                    module?.sendEvent("skipToPrevious", null)
                }
            }
            "rewind" -> {
                if (player?.isControlEnabled("seek") == true) {
                    val currentPosition = player.currentPosition ?: 0
                    val newPosition = (currentPosition - 10000).coerceAtLeast(0)
                    player.seekTo(newPosition)
                    module?.sendEvent("seekBackward", (newPosition / 1000).toInt())
                }
            }
            "fast_forward" -> {
                if (player?.isControlEnabled("seek") == true) {
                    val currentPosition = player.currentPosition ?: 0
                    val duration = player.duration ?: 0
                    val newPosition = (currentPosition + 10000).coerceAtMost(duration)
                    player.seekTo(newPosition)
                    module?.sendEvent("seekForward", (newPosition / 1000).toInt())
                }
            }
            "stop" -> {
                if (player?.isControlEnabled("stop") == true) {
                    player.stop()
                    module?.sendEvent("stop", null)
                    createMediaNotification() // Update notification nach Stop
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Media Controls",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Media playback controls"
            setShowBadge(false)
            setSound(null, null)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        this.stopNotificationAndService()
        super.onDestroy()
    }

    fun stopNotificationAndService() {
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
        mediaSession?.release()
        mediaSession = null
    }

    fun getPlayer(): MediaControlsPlayer? = player

    // Neue Methode um Notification zu starten wenn Metadaten gesetzt werden
    fun showNotificationWithMetadata() {
        hasMetadata = true
        createMediaNotification()
    }

    private inner class MediaSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Accept all connections and provide full access to player commands
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .build()

            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            super.onPostConnect(session, controller)
            // Custom initialization can be done here if needed
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            // Handle custom commands if needed
            return when (customCommand.customAction) {
                "TOGGLE_PLAY_PAUSE" -> {
                    player?.let { p ->
                        if (p.isPlaying) {
                            p.pause()
                        } else {
                            p.play()
                        }
                    }
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> Futures.immediateFuture(SessionResult(SessionError.ERROR_UNKNOWN))
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            // Return the media items as-is since we're handling metadata updates separately
            return Futures.immediateFuture(mediaItems)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            )
        }
    }
}
