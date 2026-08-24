package com.mediacontrols

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class MediaControlsService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null

    @Volatile
    private var sessionReleased = false
    private val binder = LocalBinder()
    private var mediaController: MediaController? = null
    private var notificationProvider: MediaNotificationProvider? = null

    private var becomingNoisyRegistered = false
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            pauseForOutputLoss()
        }
    }

    private var deviceCallbackRegistered = false
    private val outputDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (removedDevices == null || !removedDevices.any { isRemoteSink(it) }) return
            // A handover between two external outputs is not an output loss.
            if (remoteSinkStillPresent()) return
            pauseForOutputLoss()
        }
    }

    @Suppress("InlinedApi")
    private fun isRemoteSink(device: AudioDeviceInfo?): Boolean {
        if (device == null || !device.isSink) return false
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_DOCK,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE,
            AudioDeviceInfo.TYPE_BUS -> true
            else -> false
        }
    }

    private fun remoteSinkStillPresent(): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        return try {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isRemoteSink(it) }
        } catch (t: Throwable) {
            true
        }
    }

    @Volatile
    private var lastOutputLossPauseAt = 0L

    private fun pauseForOutputLoss() {
        val activePlayer = player ?: return

        val now = SystemClock.elapsedRealtime()
        if (now - lastOutputLossPauseAt < OUTPUT_LOSS_DEBOUNCE_MS) return
        lastOutputLossPauseAt = now

        Handler(activePlayer.applicationLooper).post {
            if (activePlayer.playWhenReady) {
                activePlayer.sendEvent(Controls.PAUSE, null)
            }
        }
    }

    companion object {
        private const val OUTPUT_LOSS_DEBOUNCE_MS = 2000L

        private const val CHANNEL_ID = "media_controls_channel"

        private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"

        const val CAR_CONNECTED_EVENT = "carConnected"
        const val CAR_DISCONNECTED_EVENT = "carDisconnected"
        var player: MediaControlsPlayer? = null
        val persistedEnabledControls = mutableMapOf<Controls, Boolean>()

        var persistedCustomButtons: List<CustomButtonSpec> = emptyList()
        var instance: MediaControlsService? = null
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

        instance = this

        if (player == null) {
            player = MediaControlsPlayer(this)
        }

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        // Create media session
        mediaSession = MediaLibrarySession.Builder(this, player!!, MediaSessionCallback())
            .setId("MediaControlsSession")
            .build()
        sessionReleased = false

        updateCustomLayout()

        player?.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateCustomLayout()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateCustomLayout()
            }
        })

        notificationProvider = MediaNotificationProvider(this)
        setMediaNotificationProvider(notificationProvider!!)

        // Create MediaController for media controls
        setupMediaController()

        try {
            ContextCompat.registerReceiver(
                this,
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            becomingNoisyRegistered = true
        } catch (t: Throwable) {
            becomingNoisyRegistered = false
        }

        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                am.registerAudioDeviceCallback(outputDeviceCallback, Handler(Looper.getMainLooper()))
                deviceCallbackRegistered = true
            }
        } catch (t: Throwable) {
            deviceCallbackRegistered = false
        }

        MediaStore.init(this)
        MediaStore.Instance.addListener(object: MediaStore.Listener {
            override fun onMediaItemsUpdated(parentId: String?, itemCount: Int) {
                mediaSession?.notifyChildrenChanged(parentId ?: "root", itemCount, null)
            }
        })

        android.util.Log.d("MediaControlsService", "Service created with new player instance")
    }

    fun updateCustomLayout() {
        mediaSession?.setMediaButtonPreferences(player?.getAvailableCustomCommands()?.toList() ?: emptyList())
    }

    fun refreshAvailableCommands() {
        val session = mediaSession ?: return
        val sessionCommands = buildSessionCommands()
        val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        for (controller in session.connectedControllers) {
            session.setAvailableCommands(controller, sessionCommands, playerCommands)
        }
    }

    private fun buildSessionCommands(): SessionCommands {
        val commands = mutableListOf<SessionCommand>()
        commands += CustomCommandButton.entries.map { it.commandButton.sessionCommand!! }
        player?.getCustomButtons()?.forEach { commands += it.toSessionCommand() }
        return MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .addSessionCommands(commands)
            .build()
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Guard against NPE when media button events arrive after player/session is released
        // or before they are initialized. The super implementation calls
        // MediaSessionImpl.applyMediaButtonKeyEvent which does checkNotNull(player).
        // A released-but-not-yet-cleared session must be treated the same way:
        // super hands it to MediaSessionService.addSession, which asserts on
        // an already released session.
        val session = mediaSession
        if (session == null || sessionReleased || player == null) {
            return Service.START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession?.takeIf { !sessionReleased }
    }

    override fun onDestroy() {
        this.stopNotificationAndService()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        this.stopNotificationAndService()
        super.onTaskRemoved(rootIntent)
    }

    fun stopNotificationAndService() {
        player?.sendEvent(Controls.STOP, null)

        player?.runCatching {
            stop()
            clearMediaItems()
        }

        if (becomingNoisyRegistered) {
            try {
                unregisterReceiver(becomingNoisyReceiver)
            } catch (ignored: Exception) {}
            becomingNoisyRegistered = false
        }

        if (deviceCallbackRegistered) {
            try {
                (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                    ?.unregisterAudioDeviceCallback(outputDeviceCallback)
            } catch (ignored: Exception) {}
            deviceCallbackRegistered = false
        }

        mediaController?.runCatching { release() }
        mediaController = null

        val sessionToRelease = mediaSession
        sessionReleased = true
        mediaSession = null
        sessionToRelease?.runCatching { release() }

        player?.releaseFocus()
        player?.runCatching {
            cleanup()
            release()
        }
        player = null

        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        notificationProvider?.lastNotificationId?.let { id ->
            getSystemService(NotificationManager::class.java)?.cancel(id)
        }
        notificationProvider = null
        if (instance === this) {
            instance = null
        }

        stopSelf()
    }

    fun getPlayer(): MediaControlsPlayer? = player

    fun isCarConnected(): Boolean {
        val session = mediaSession ?: return false
        return try {
            session.connectedControllers.any { it.packageName == ANDROID_AUTO_PACKAGE }
        } catch (t: Throwable) {
            false
        }
    }

    private inner class MediaSessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            if (controller.packageName == ANDROID_AUTO_PACKAGE) {
                MediaControlsModule.Instance?.sendCustomEvent(CAR_CONNECTED_EVENT, null)
            }

            // Accept all connections and provide full access to player commands
            val sessionCommands = buildSessionCommands()

            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            if (controller.packageName == ANDROID_AUTO_PACKAGE) {
                MediaControlsModule.Instance?.sendCustomEvent(CAR_DISCONNECTED_EVENT, null)
            }
            super.onDisconnected(session, controller)
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            super.onPostConnect(session, controller)
            //mediaSession?.setCustomLayout(CustomCommandButton.entries.map { c -> c.commandButton })
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
                val keyEvent = intent.parcelable<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null && keyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            player?.seekToNextMediaItem()
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            player?.seekToPreviousMediaItem()
                            return true
                        }
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
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
                else -> {
                    val spec = player?.findCustomButton(customCommand.customAction)
                    if (spec != null) {
                        MediaControlsModule.Instance?.sendCustomEvent(spec.eventId, null)
                            ?: EventEmitter.sendEvent(this@MediaControlsService, spec.eventId, null)
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    } else {
                        Futures.immediateFuture(SessionResult(SessionError.ERROR_UNKNOWN))
                    }
                }
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
            val query = mediaItems.singleOrNull()?.requestMetadata?.searchQuery
            if (query != null) {
                val resolved = MediaStore.Instance.resolveSearch(query)
                if (resolved.isEmpty()) {
                    return Futures.immediateFailedFuture(
                        UnsupportedOperationException("nothing in the library matches " + query)
                    )
                }
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(resolved, 0, C.TIME_UNSET)
                )
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                MediaStore.Instance.getItem(mediaId)
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                MediaStore.Instance.getRoot()
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(
                MediaStore.Instance.getChildren(parentId, page, pageSize)
            )
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(
                MediaStore.Instance.search(query, page, pageSize)
            )
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            session.notifySearchResultChanged(browser, query, MediaStore.Instance.searchCount(query), params)
            return Futures.immediateFuture(
                LibraryResult.ofVoid()
            )
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return Futures.immediateFuture(
                MediaStore.Instance.getLastMediaItem()
            )
        }
    }
}
