package com.mediacontrols

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.media3.common.util.UnstableApi
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = MediaControlsModule.NAME)
@UnstableApi
class MediaControlsModule(reactContext: ReactApplicationContext) :
  NativeMediaControlsSpec(reactContext) {

  private var mediaService: MediaControlsService? = null
  private var serviceBound = false

  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      val binder = service as? MediaControlsService.LocalBinder
      mediaService = binder?.getService()
      serviceBound = true
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      mediaService = null
      serviceBound = false
    }
  }
  init {
    MediaStore.init(reactContext)
    if (MediaControlsService.player == null) {
      MediaControlsService.player = MediaControlsPlayer(reactContext)
    }
    Instance = this
  }

  override fun getName(): String {
    return NAME
  }

  @ReactMethod
  override fun setControlEnabled(name: String, enabled: Boolean) {
    val control = Controls.fromString(name) ?: throw IllegalArgumentException("Invalid control name: $name")
    MediaControlsService.player?.setControlEnabled(control, enabled)
    MediaControlsService.instance?.updateCustomLayout()
  }

  @ReactMethod
  override fun updateMetadata(metadata: ReadableMap, promise: Promise) {
    try {
      // stopMediaNotification() releases and nulls the singleton player. Recreate it
      // before (re)starting the service so this metadata isn't dropped onto a null
      // player while the service restarts asynchronously (empty notification on replay).
      if (MediaControlsService.player == null) {
        MediaControlsService.player = MediaControlsPlayer(reactApplicationContext)
      }
      // Start service on first updateMetadata call
      ensureServiceStarted()

      val trackMetadata = MediaTrackMetadata(
        id = if (metadata.hasKey("id")) metadata.getString("id") else null,
        title = if (metadata.hasKey("title")) metadata.getString("title") else null,
        artist = if (metadata.hasKey("artist")) metadata.getString("artist") else null,
        album = if (metadata.hasKey("album")) metadata.getString("album") else null,
        duration = if (metadata.hasKey("duration")) metadata.getDouble("duration") else null,
        artwork = if (metadata.hasKey("artwork")) metadata.getString("artwork") else null,
        position = if (metadata.hasKey("position")) metadata.getDouble("position") else null,
        isPlaying = if (metadata.hasKey("isPlaying")) metadata.getBoolean("isPlaying") else null,
        isLoading = if (metadata.hasKey("isLoading")) metadata.getBoolean("isLoading") else null,
        shuffleMode = if (metadata.hasKey("shuffle")) metadata.getBoolean("shuffle") else null,
        repeatMode = if (metadata.hasKey("repeatMode")) metadata.getString("repeatMode") else null,
      )

      MediaControlsService.player?.updateMetadata(trackMetadata)
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("UPDATE_METADATA_ERROR", "Failed to update metadata: ${e.message}", e)
    }
  }

  @ReactMethod
  override fun stopMediaNotification(promise: Promise) {
    try {
      stopMediaService()
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("STOP_NOTIFICATION_ERROR", "Failed to stop notification: ${e.message}", e)
    }
  }

  @ReactMethod
  override fun enableAudioInterruption(enabled: Boolean, promise: Promise) {
    try {
      MediaControlsService.player?.setAudioInterruptionEnabled(enabled)
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("AUDIO_INTERRUPTION_ERROR", "Failed to enable audio interruption: ${e.message}", e)
    }
  }

  override fun enableBackgroundMode(enabled: Boolean) {
    // NOOP on android
  }

  override fun shutdown() {
    this.stopMediaService();
    Instance = null
  }

  @SuppressLint("NewApi")
  override fun isCarConnected(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      // No route query before API 31, so fall back to the controller list.
      return MediaControlsService.instance?.isCarConnected() ?: false
    }
    val audioManager =
      reactApplicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    return try {
      val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
      audioManager.getAudioDevicesForAttributes(attributes)
        .any { it.type == AudioDeviceInfo.TYPE_REMOTE_SUBMIX }
    } catch (t: Throwable) {
      false
    }
  }

  override fun setMediaLibrary(library: ReadableMap?) {
    MediaStore.Instance.build(library)
  }

  override fun setCustomButtons(buttons: ReadableArray?) {
    val specs = mutableListOf<CustomButtonSpec>()
    if (buttons != null) {
      for (i in 0 until buttons.size()) {
        val map = buttons.getMap(i) ?: continue
        val eventId = map.getString("eventId") ?: continue
        val icon = map.getString("icon") ?: continue
        val displayName = if (map.hasKey("displayName")) map.getString("displayName") else null
        specs.add(CustomButtonSpec(eventId, icon, displayName))
      }
    }
    MediaControlsService.persistedCustomButtons = specs
    MediaControlsService.instance?.refreshAvailableCommands()
    MediaControlsService.instance?.updateCustomLayout()
  }

  @ReactMethod
  fun getControlsEnabled(promise: Promise) {
    try {
      ensureServiceStarted()
      val player = MediaControlsService.player
      val controls = Controls.entries.associate{ Pair(it.code, player?.isControlEnabled(it) ?: false) }

      val result = Arguments.createMap()
      controls.forEach { (key, value) ->
        result.putBoolean(key, value)
      }
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("GET_CONTROLS_ERROR", "Failed to get controls state: ${e.message}", e)
    }
  }

  @ReactMethod
  fun getCurrentPlayerState(promise: Promise) {
    try {
      ensureServiceStarted()
      val player = MediaControlsService.player
      val result = Arguments.createMap()

      if (player != null) {
        result.putBoolean("isPlaying", player.isPlaying)
        result.putBoolean("playWhenReady", player.playWhenReady)
        result.putInt("playbackState", player.playbackState)
        result.putString("currentTitle", player.mediaMetadata.title?.toString() ?: "Unknown")
      } else {
        result.putBoolean("isPlaying", false)
        result.putString("error", "Player not available")
      }

      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("GET_PLAYER_STATE_ERROR", "Failed to get player state: ${e.message}", e)
    }
  }

  private fun ensureServiceStarted() {
    startMediaService()
  }

  private fun isServiceRunning(serviceClass: Class<*>): Boolean {
    val activityManager = reactApplicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in activityManager.getRunningServices(Int.Companion.MAX_VALUE)) {
      if (serviceClass.name == service.service.className) {
        return true
      }
    }
    return false
  }

  fun sendEvent(eventName: Controls, data: WritableMap?) {
    sendCustomEvent(eventName.code, data)
  }

  fun sendCustomEvent(eventId: String, data: WritableMap?) {
    val eventData = Arguments.createMap().apply {
        putString("command", eventId)
        putMap("data", data)
    }
    emitOnEvent(eventData)
  }

  private fun startMediaService() {
    val intent = Intent(reactApplicationContext, MediaControlsService::class.java)
    reactApplicationContext.startService(intent)
    if (!serviceBound) {
      reactApplicationContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
  }

  private fun stopMediaService() {
    mediaService?.stopNotificationAndService()

    if (serviceBound) {
      reactApplicationContext.unbindService(serviceConnection)
      serviceBound = false
    }

    val intent = Intent(reactApplicationContext, MediaControlsService::class.java)
    reactApplicationContext.stopService(intent)
    mediaService = null
  }

  override fun invalidate() {
    super.invalidate()
    stopMediaService()
  }

  companion object {
    const val NAME = "MediaControls"

    var Instance : MediaControlsModule? = null
  }
}
