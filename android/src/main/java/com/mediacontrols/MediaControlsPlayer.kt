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
    private var audioInterruptionEnabled = true

    // Control states
    private val enabledControls = mutableMapOf<String, Boolean>().apply {
        put("play", true)
        put("pause", true)
        put("stop", true)
        put("next", true)
        put("previous", true)
        put("seek", true)
    }

    // Player listener to track state changes
    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            android.util.Log.d("MediaControlsPlayer", "onPlayWhenReadyChanged: $playWhenReady, reason: $reason")
            //emitPlaybackStateChanged(playWhenReady)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            android.util.Log.d("MediaControlsPlayer", "onPlaybackStateChanged: $playbackState, playWhenReady: $playWhenReady")
            val isPlaying = playbackState == Player.STATE_READY && playWhenReady
            //emitPlaybackStateChanged(isPlaying)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            android.util.Log.d("MediaControlsPlayer", "onIsPlayingChanged: $isPlaying")
            //emitPlaybackStateChanged(isPlaying)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                emitSeekEvent(newPosition.positionMs)
            }
        }
    }

    init {
        // Add listener to track state changes from external sources (notifications, etc.)
        addListener(playerListener)
    }

    override fun getState(): State = currentState

    fun getListener(): Player.Listener = playerListener

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        updateState { builder ->
            builder.setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
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

        module.sendEvent("stop", null)
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
                module.sendEvent("skipToNext", null)
            }
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                module.sendEvent("skipToPrevious", null)
            }
            Player.COMMAND_SEEK_FORWARD -> {
                val positionSeconds = (positionMs / 1000).toInt()
                module.sendEvent("seekForward", positionSeconds)
            }
            Player.COMMAND_SEEK_BACK -> {
                val positionSeconds = (positionMs / 1000).toInt()
                module.sendEvent("seekBackward", positionSeconds)
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

    // Custom methods for React Native integration
    fun updateMetadata(metadata: MediaTrackMetadata) {
        this.currentMetadata = metadata

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(metadata.title)
            .setArtist(metadata.artist)
            .setAlbumTitle(metadata.album)
            .setDurationMs(metadata.duration?.times(1000)?.toLong())
            .apply {
                metadata.artwork?.let { artworkUrl ->
                    setArtworkUri(artworkUrl.toUri())
                }
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(metadata.title) // Use title as media ID
            .setMediaMetadata(mediaMetadata)
            .build()

        val mediaItemData = MediaItemData.Builder(metadata.title)
            .setMediaItem(mediaItem)
            .setDefaultPositionUs(metadata.position?.times(1_000_000)?.toLong() ?: 0)
            .setDurationUs(metadata.duration?.times(1_000_000)?.toLong() ?: androidx.media3.common.C.TIME_UNSET)
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
        }
    }

    fun setControlEnabled(controlName: String, enabled: Boolean) {
        enabledControls[controlName] = enabled

        // Update available commands based on enabled controls
        val availableCommands = mutableSetOf<Int>().apply {
            if (enabledControls["play"] == true || enabledControls["pause"] == true) add(Player.COMMAND_PLAY_PAUSE)
            if (enabledControls["stop"] == true) add(Player.COMMAND_STOP)
            if (enabledControls["next"] == true) add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            if (enabledControls["previous"] == true) add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            if (enabledControls["seek"] == true) add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)

            add(Player.COMMAND_PREPARE)
            add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            add(Player.COMMAND_GET_METADATA)
        }

        updateState { builder ->
            builder.setAvailableCommands(Player.Commands.Builder().addAll(*availableCommands.toIntArray()).build())
        }
    }

    fun isControlEnabled(controlName: String): Boolean {
        return enabledControls[controlName] ?: false
    }

    fun setAudioInterruptionEnabled(enabled: Boolean) {
        audioInterruptionEnabled = enabled
        // Audio focus handling can be implemented here if needed
        // For now, we just store the state
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
        val command = if (isPlaying) "play" else "pause"
        module.sendEvent(command, null)
    }

    private fun emitSeekEvent(positionMs: Long) {
        val positionSeconds = (positionMs / 1000).toInt()
        module.sendEvent("seek", positionSeconds)
    }

    fun cleanup() {
        Handler(applicationLooper).post {
            removeListener(playerListener)
        }
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
    val isPlaying: Boolean? = null
)
