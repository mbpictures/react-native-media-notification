package com.mediacontrols

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.webkit.URLUtil
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.ByteArrayOutputStream


@UnstableApi
class MediaControlsPlayer(
    private val context: Context
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentState = State.Builder().setAvailableCommands(getCommands()).build()

    // Track metadata
    private var currentMetadata: MediaTrackMetadata? = null

    // Audio interruption
    private var audioInterruptionEnabled = false

    private var audioFocusListener = AudioFocusListener(context, this)

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
                .setContentPositionMs(currentState.contentPositionMsSupplier.get())
        }

        // Emit event to React Native
        emitPlaybackStateChanged(playWhenReady)

        return Futures.immediateFuture(null)
    }

    override fun handlePrepare(): ListenableFuture<*> {
        return Futures.immediateFuture(null)
    }

    override fun handleStop(): ListenableFuture<*> {
        updateState { builder ->
            builder.setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(Player.STATE_IDLE)
        }

        sendEvent(Controls.STOP, null)
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
                sendEvent(Controls.NEXT, null)
            }
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                sendEvent(Controls.PREVIOUS, null)
            }
            Player.COMMAND_SEEK_FORWARD -> {
                val positionSeconds = (positionMs / 1000).toInt()
                sendEvent(Controls.SEEK_FORWARD, Arguments.createMap().apply { putInt("seekPosition", positionSeconds) })
            }
            Player.COMMAND_SEEK_BACK -> {
                val positionSeconds = (positionMs / 1000).toInt()
                sendEvent(Controls.SEEK_BACKWARD, Arguments.createMap().apply { putInt("seekPosition", positionSeconds) })
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
        sendEvent(
            Controls.SET_MEDIA_ITEMS,
            Arguments.createMap().apply {
                putArray(
                    "mediaItems",
                    Arguments.createArray().apply { mediaItems.forEach { item -> pushString(item.mediaId) } }
                )
            }
        )
        return Futures.immediateFuture(null)
    }

    fun emitShuffleClicked() {
        sendEvent(Controls.SHUFFLE, null)
    }

    fun emitRepeatClicked() {
        sendEvent(Controls.REPEAT_MODE, null)
    }

    // Custom methods for React Native integration
    fun updateMetadata(metadata: MediaTrackMetadata) {
        if (metadata.isPlaying == true && audioInterruptionEnabled) {
            audioFocusListener.requestAudioFocus()
        }

        this.currentMetadata = this.currentMetadata?.merge(metadata) ?: metadata

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(this.currentMetadata!!.title)
            .setArtist(this.currentMetadata!!.artist)
            .setAlbumTitle(this.currentMetadata!!.album)
            .setDurationMs(this.currentMetadata!!.duration?.times(1000)?.toLong())
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .also {
                this.currentMetadata!!.artwork?.let { artworkUrl ->
                    it.setArtwork(artworkUrl)
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
            .setDefaultPositionUs(this.currentMetadata!!.position?.times(1_000_000)?.toLong() ?: 0)
            .setDurationUs(this.currentMetadata!!.duration?.times(1_000_000)?.toLong() ?: androidx.media3.common.C.TIME_UNSET)
            .setIsSeekable(true)
            .build()

        updateState { builder ->
            builder.setPlaylist(listOf(mediaItemData))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(this.currentMetadata!!.position?.times(1000)?.toLong() ?: 0)
                .setPlayWhenReady(
                    this.currentMetadata!!.isPlaying ?: false,
                    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                )
                .setPlaybackState(Player.STATE_READY)
                .setAvailableCommands(state.availableCommands)
                .setRepeatMode(this.currentMetadata!!.repeatMode)
                .setShuffleModeEnabled(this.currentMetadata!!.shuffleMode)
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

    private fun MediaMetadata.Builder.setArtwork(artwork: String?): MediaMetadata.Builder {
        if (artwork == null) {
            return this
        }
        if (URLUtil.isValidUrl(artwork)) {
            this.setArtworkUri(artwork.toUri())
        } else {
            val helper = ResourceDrawableIdHelper.getInstance()
            val image = helper.getResourceDrawable(context, artwork)

            val bitmap = if (image is BitmapDrawable) {
                image.bitmap
            } else {
                BitmapFactory.decodeFile(artwork)
            }
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            this.setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_OTHER)
        }
        return this
    }

    fun setControlEnabled(controlName: Controls, enabled: Boolean) {
        enabledControls[controlName] = enabled
    }

    fun getCommands(): Player.Commands {
        val availableCommands = mutableSetOf<Int>().apply {
            add(Player.COMMAND_PLAY_PAUSE)
            add(Player.COMMAND_STOP)
            add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            add(Player.COMMAND_SEEK_FORWARD)
            add(Player.COMMAND_SEEK_BACK)
            add(Player.COMMAND_SET_SHUFFLE_MODE)
            add(Player.COMMAND_SET_REPEAT_MODE)
            add(Player.COMMAND_PREPARE)
            add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            add(Player.COMMAND_GET_METADATA)
            add(Player.COMMAND_SET_MEDIA_ITEM)
        }
        return Player.Commands.Builder().addAll(*availableCommands.toIntArray()).build()
    }

    fun getAvailableCustomCommands(): Set<CommandButton> {
        return mutableSetOf<CommandButton>().apply {
            if (isControlEnabled(Controls.PREVIOUS)) add(CustomCommandButton.PREVIOUS.commandButton)
            if (isControlEnabled(Controls.NEXT)) add(CustomCommandButton.NEXT.commandButton)

            if (isControlEnabled(Controls.SEEK_BACKWARD)) add(CustomCommandButton.REWIND.commandButton)
            if (isControlEnabled(Controls.SEEK_FORWARD)) add(CustomCommandButton.FORWARD.commandButton)

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
        sendEvent(command, null)
    }

    private fun emitSeekEvent(positionMs: Long) {
        val positionSeconds = (positionMs / 1000).toInt()
        sendEvent(Controls.SEEK, Arguments.createMap().apply { putInt("seekPosition", positionSeconds) })
    }

    fun cleanup() {
        scope.cancel()
    }

    fun sendEvent(command: Controls, data: WritableMap?) {
        EventEmitter.sendEvent(context, command, data)
    }
}

// Data class for metadata
data class MediaTrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Double? = null,
    val artwork: String? = null,
    val position: Double? = null,
    val isPlaying: Boolean? = null,
    val repeatMode: String? = null,
    val shuffleMode: Boolean? = null
) {

    fun merge(other: MediaTrackMetadata): MediaTrackMetadata = MediaTrackMetadata(
        title = other.title ?: this.title,
        artist = other.artist ?: this.artist,
        album = other.album ?: this.album,
        duration = other.duration ?: this.duration,
        artwork = other.artwork ?: this.artwork,
        position = other.position ?: this.position,
        isPlaying = other.isPlaying ?: this.isPlaying,
        repeatMode = other.repeatMode ?: this.repeatMode,
        shuffleMode = other.shuffleMode ?: this.shuffleMode
    )
}
