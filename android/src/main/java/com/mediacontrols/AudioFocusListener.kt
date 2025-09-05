package com.mediacontrols

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Build
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import com.facebook.react.bridge.ReactApplicationContext

@UnstableApi
class AudioFocusListener(
    context: ReactApplicationContext,
    private val module: MediaControlsModule,
    private val player: MediaControlsPlayer
) : OnAudioFocusChangeListener {

    private val mAudioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mFocusRequest: AudioFocusRequest? = null

    private var mPlayOnAudioFocus = false
    private var ducked = false

    private var hasFocus = false

    override fun onAudioFocusChange(focusChange: Int) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            abandonAudioFocus()
            mPlayOnAudioFocus = false
            module.sendEvent(Controls.PAUSE, null)
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            Handler(player.applicationLooper).post {
                if (player.isPlaying) {
                    mPlayOnAudioFocus = true
                    module.sendEvent(Controls.PAUSE, null)
                }
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            module.sendEvent(Controls.DUCK, null)
            ducked = true
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            if (mPlayOnAudioFocus) {
                module.sendEvent(Controls.PLAY, null)
            }
            if (ducked) {
                module.sendEvent(Controls.UN_DUCK, null)
            }
            mPlayOnAudioFocus = false
            ducked = false
        }
    }

    fun requestAudioFocus() {
        if (hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(this).build()

            hasFocus = mAudioManager.requestAudioFocus(mFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            hasFocus = mAudioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandonAudioFocus() {
        if (!hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mFocusRequest != null) {
            mAudioManager.abandonAudioFocusRequest(mFocusRequest!!)
        } else mAudioManager.abandonAudioFocus(this)

        hasFocus = false
    }
}