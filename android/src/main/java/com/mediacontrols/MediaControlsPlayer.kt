package com.mediacontrols

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.facebook.react.bridge.ReactApplicationContext
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.core.net.toUri
import androidx.media3.session.CommandButton

@UnstableApi
class MediaControlsPlayer(
    reactContext: ReactApplicationContext,
    private val module: MediaControlsModule,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentState = State.Builder().build()

    // Track metadata
    private var currentMetadata: MediaTrackMetadata? = null

    // Audio interruption
    private var audioInterruptionEnabled = false

    private var audioFocusListener = AudioFocusListener(reactContext, module, this)

    // Control states
    private val enabledControls = mutableMapOf<Controls, Boolean>()

    override fun getState(): State = currentState

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady && audioInterruptionEnabled) {
            audioFocusListener.requestAudioFocus()
        }

        updateState { builder ->
            builder
                .setPlayWhenReady(
                    playWhenReady,
                    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                )
                .setPlaybackState(
                    if (playWhenReady) Player.STATE_READY else Player.STATE_IDLE
                )
                .setContentPositionMs(currentState.contentPositionMsSupplier.get())
        }

        // Emit event to React Native
        emitPlaybackStateChanged(playWhenReady)

        return Futures.immediateFuture(null)
    }

    override fun handlePrepare(): ListenableFuture<*> {
        updateState { builder ->
            builder.setPlaybackState(Player.STATE_READY)
        }
        return Futures.immediateFuture(null)
    }

    override fun handleStop(): ListenableFuture<*> {
        updateState { builder ->
            builder.setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(Player.STATE_IDLE)
        }

        module.sendEvent(Controls.STOP, null)
        return Futures.immediateFuture(null)
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        updateState { builder ->
            builder.setCurrentMediaItemIndex(mediaItemIndex)
                .setContentPositionMs(positionMs)
        }

        // Handle different seek commands
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                module.sendEvent(Controls.NEXT, null)
            }
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                module.sendEvent(Controls.PREVIOUS, null)
            }
            Player.COMMAND_SEEK_FORWARD -> {
                val positionSeconds = (positionMs / 1000).toInt()
                module.sendEvent(Controls.SEEK_FORWARD, positionSeconds)
            }
            Player.COMMAND_SEEK_BACK -> {
                val positionSeconds = (positionMs / 1000).toInt()
                module.sendEvent(Controls.SEEK_BACKWARD, positionSeconds)
            }
            else -> {
                emitSeekEvent(positionMs)
            }
        }

        return Futures.immediateFuture(null)
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        val mediaItemDataList = mediaItems.map { mediaItem ->
            MediaItemData.Builder(mediaItem.mediaId)
                .setMediaItem(mediaItem)
                .build()
        }

        updateState { builder ->
            builder.setPlaylist(mediaItemDataList)
                .setCurrentMediaItemIndex(startIndex)
                .setContentPositionMs(startPositionMs)
        }
        return Futures.immediateFuture(null)
    }

    fun emitShuffleClicked() {
        module.sendEvent(Controls.SHUFFLE, null)
    }

    fun emitRepeatClicked() {
        module.sendEvent(Controls.REPEAT_MODE, null)
    }

    // Custom methods for React Native integration
    fun updateMetadata(metadata: MediaTrackMetadata) {
        if (metadata.isPlaying == true && audioInterruptionEnabled) {
            audioFocusListener.requestAudioFocus()
        }

        this.currentMetadata = metadata

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(metadata.title)
            .setArtist(metadata.artist)
            .setAlbumTitle(metadata.album)
            .setDurationMs(metadata.duration?.times(1000)?.toLong())
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                metadata.artwork?.let { artworkUrl ->
                    setArtworkUri(artworkUrl.toUri())
                }
            }
            .build()

        // Create unique media ID for Android Auto
        val mediaId = "${metadata.title}_${metadata.artist}".replace(" ", "_")

        val mediaItem = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri("content://media/external/audio/media/1") // Placeholder URI for Android Auto
            .setMediaMetadata(mediaMetadata)
            .build()

        val mediaItemData = MediaItemData.Builder(mediaId)
            .setMediaItem(mediaItem)
            .setDefaultPositionUs(metadata.position?.times(1_000_000)?.toLong() ?: 0)
            .setDurationUs(metadata.duration?.times(1_000_000)?.toLong() ?: androidx.media3.common.C.TIME_UNSET)
            .setIsSeekable(true)
            .build()

        updateState { builder ->
            builder.setPlaylist(listOf(mediaItemData))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(metadata.position?.times(1000)?.toLong() ?: 0)
                .setPlayWhenReady(
                    metadata.isPlaying ?: false,
                    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                )
                .setPlaybackState(
                    if (metadata.isPlaying == true) Player.STATE_READY else Player.STATE_IDLE
                )
                .setAvailableCommands(state.availableCommands)
                .setRepeatMode(metadata.repeatMode)
                .setShuffleModeEnabled(metadata.shuffleMode)
        }
    }

    private fun State.Builder.setRepeatMode(mode: String?): State.Builder {
        val repeatMode = when (mode) {
            "off" -> Player.REPEAT_MODE_OFF
            "one" -> Player.REPEAT_MODE_ONE
            "all" -> Player.REPEAT_MODE_ALL
            else -> null
        }
        if (repeatMode == null) {
            return this
        }
        return this.setRepeatMode(repeatMode)
    }

    private fun State.Builder.setShuffleModeEnabled(enabled: Boolean?): State.Builder {
        if (enabled == null) {
            return this
        }
        return this.setShuffleModeEnabled(enabled)
    }

    fun setControlEnabled(controlName: Controls, enabled: Boolean) {
        enabledControls[controlName] = enabled

        // Update available commands based on enabled controls
        val availableCommands = mutableSetOf<Int>().apply {
            if (enabledControls[Controls.PLAY] == true || enabledControls[Controls.PAUSE] == true) add(Player.COMMAND_PLAY_PAUSE)
            if (enabledControls[Controls.STOP] == true) add(Player.COMMAND_STOP)
            if (enabledControls[Controls.NEXT] == true) add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            if (enabledControls[Controls.PREVIOUS] == true) add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            if (enabledControls[Controls.SEEK] == true) add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            if (enabledControls[Controls.SEEK_FORWARD] == true) add(Player.COMMAND_SEEK_FORWARD)
            if (enabledControls[Controls.SEEK_BACKWARD] == true) add(Player.COMMAND_SEEK_BACK)
            if (enabledControls[Controls.SHUFFLE] == true) add(Player.COMMAND_SET_SHUFFLE_MODE)
            if (enabledControls[Controls.REPEAT_MODE] == true) add(Player.COMMAND_SET_REPEAT_MODE)

            add(Player.COMMAND_PREPARE)
            add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            add(Player.COMMAND_GET_METADATA)
        }

        updateState { builder ->
            builder.setAvailableCommands(Player.Commands.Builder().addAll(*availableCommands.toIntArray()).build())
        }
    }

    fun getAvailableCustomCommands(): Set<CommandButton> {
        return mutableSetOf<CommandButton>().apply {
            if(isControlEnabled(Controls.SHUFFLE)) {
                if (state.shuffleModeEnabled) {
                    add(CustomCommandButton.SHUFFLE_ON.commandButton)
                } else {
                    add(CustomCommandButton.SHUFFLE_OFF.commandButton)
                }
            }
            if (isControlEnabled(Controls.REPEAT_MODE)) {
                when (state.repeatMode) {
                    REPEAT_MODE_OFF -> add(CustomCommandButton.REPEAT_OFF.commandButton)
                    REPEAT_MODE_ONE -> add(CustomCommandButton.REPEAT_ONE.commandButton)
                    REPEAT_MODE_ALL -> add(CustomCommandButton.REPEAT_ALL.commandButton)
                }
            }
            if (isControlEnabled(Controls.SEEK_BACKWARD)) add(CustomCommandButton.REWIND.commandButton)
            if (isControlEnabled(Controls.SEEK_FORWARD)) add(CustomCommandButton.FORWARD.commandButton)
        }
    }

    fun isControlEnabled(controlName: Controls): Boolean {
        return enabledControls[controlName] ?: false
    }

    fun setAudioInterruptionEnabled(enabled: Boolean) {
        if (audioInterruptionEnabled == enabled) return
        audioInterruptionEnabled = enabled

        if (enabled) {
            audioFocusListener.requestAudioFocus()
        } else {
            audioFocusListener.abandonAudioFocus()
        }
    }

    fun releaseFocus() {
        audioFocusListener.abandonAudioFocus()
    }

    fun isAudioInterruptionEnabled(): Boolean = audioInterruptionEnabled

    private fun updateState(updater: (State.Builder) -> State.Builder) {
        val newState = updater(currentState.buildUpon()).build()
        currentState = newState

        // Ensure invalidateState is called on the correct thread
        if (Looper.myLooper() == applicationLooper) {
            invalidateState()
        } else {
            Handler(applicationLooper).post {
                invalidateState()
            }
        }
    }

    private fun emitPlaybackStateChanged(isPlaying: Boolean) {
        val command = if (isPlaying) Controls.PLAY else Controls.PAUSE
        module.sendEvent(command, null)
    }

    private fun emitSeekEvent(positionMs: Long) {
        val positionSeconds = (positionMs / 1000).toInt()
        module.sendEvent(Controls.SEEK, positionSeconds)
    }

    fun cleanup() {
        scope.cancel()
    }
}

// Data class for metadata
data class MediaTrackMetadata(
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Double? = null,
    val artwork: String? = null,
    val position: Double? = null,
    val isPlaying: Boolean? = null,
    val repeatMode: String? = null,
    val shuffleMode: Boolean? = null
)
