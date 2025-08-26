package com.mediacontrols

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap

@UnstableApi
class EventEmitter {
    companion object {
        fun sendEvent(context: Context, command: Controls, data: WritableMap?) {
            if (isAppInForeground(context) && MediaControlsModule.Instance != null) {
                Log.d("EventEmitter", "App is in foreground, sending event directly to MediaControlsModule")
                MediaControlsModule.Instance?.sendEvent(command, data)
                return
            }

            try {
                Log.d("EventEmitter", "App is in background, sending event to headless task")
                val backgroundIntent = Intent(context, MediaControlsHeadlessTask::class.java)
                backgroundIntent.putExtra("command", command.code)
                backgroundIntent.putExtra("data", Arguments.toBundle(data))
                val name: ComponentName? = context.startService(backgroundIntent)
                if (name != null) {
                    HeadlessJsTaskService.acquireWakeLockNow(context)
                }
            } catch (ex: IllegalStateException) {
                // By default, data only messages are "default" priority and cannot trigger Headless tasks
                Log.e("EventEmitter", "Error while sending command to headless task", ex)
            }
        }

        fun isAppInForeground(context: Context): Boolean {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningAppProcesses = activityManager.runningAppProcesses ?: return false
            return runningAppProcesses.any { it.processName == context.packageName && it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
        }
    }
}