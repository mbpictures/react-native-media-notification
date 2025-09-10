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
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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

    companion object {
        private const val CHANNEL_ID = "media_controls_channel"
        var reactContext: ReactApplicationContext? = null
        var player: MediaControlsPlayer? = null
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaControlsService = this@MediaControlsService
    }

    override fun onBind(intent: Intent?): IBinder {
        return if (intent?.action == "androidx.media3.session.MediaSessionService" ||
                   intent?.action == "android.media.browse.MediaBrowserService") {
            val binder = super.onBind(intent)
            binder ?: this.binder
        } else {
            binder
        }
    }

    override fun onCreate() {
        super.onCreate()

        // TODO: handle headless service with HeadlessJsTask
        if (player == null || reactContext == null) {
            return
        }

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        // Create media session
        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(MediaSessionCallback())
            .setId("MediaControlsSession")
            .build()

        updateCustomLayout()

        player?.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateCustomLayout()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateCustomLayout()
            }
        })

        setMediaNotificationProvider(MediaNotificationProvider(this))

        // Create MediaController for media controls
        setupMediaController()

        android.util.Log.d("MediaControlsService", "Service created with new player instance")
    }

    private fun updateCustomLayout() {
        mediaSession?.setMediaButtonPreferences(player!!.getAvailableCustomCommands().toList())
    }

    private fun setupMediaController() {
        mediaSession?.let { session ->
            val sessionToken = SessionToken(this, ComponentName(this, MediaControlsService::class.java))

            val controllerFuture = MediaController.Builder(this, sessionToken)
                .buildAsync()

            controllerFuture.addListener({
                try {
                    mediaController = controllerFuture.get()
                } catch (e: Exception) {
                    android.util.Log.e("MediaControlsService", "Failed to create MediaController", e)
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(this))
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // fixes crash according to: https://github.com/androidx/media/issues/422#issuecomment-2308495584
        mediaSession = null
        super.onTaskRemoved(rootIntent)
    }

    fun stopNotificationAndService() {
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
        player?.releaseFocus()
        mediaSession?.runCatching {
            release()
            mediaSession = null
        }
    }

    fun getPlayer(): MediaControlsPlayer? = player

    private inner class MediaSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Accept all connections and provide full access to player commands
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .addSessionCommands(CustomCommandButton.entries.map { c -> c.commandButton.sessionCommand!! })
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
            //mediaSession?.setCustomLayout(CustomCommandButton.entries.map { c -> c.commandButton })
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
                CustomCommandButton.PREVIOUS.customAction -> {
                    player?.seekToPreviousMediaItem()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CustomCommandButton.FORWARD.customAction -> {
                    player?.seekForward()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CustomCommandButton.REWIND.customAction -> {
                    player?.seekBack()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CustomCommandButton.NEXT.customAction -> {
                    player?.seekToNextMediaItem()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CustomCommandButton.SHUFFLE_ON.customAction, CustomCommandButton.SHUFFLE_OFF.customAction -> {
                    player?.emitShuffleClicked()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CustomCommandButton.REPEAT_ONE.customAction, CustomCommandButton.REPEAT_OFF.customAction, CustomCommandButton.REPEAT_ALL.customAction -> {
                    player?.emitRepeatClicked()
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
