package com.mediacontrols

import android.content.Intent
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

class MediaControlsHeadlessTask : HeadlessJsTaskService() {
    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? {
        val extras = intent?.extras
        if (intent == null || extras == null) return null

        val data = Arguments.createMap()
        data.putString("command", intent.parcelable("message"))
        data.putMap("data", Arguments.fromBundle(intent.parcelable("data")))

        return HeadlessJsTaskConfig(
            TASK_KEY,
            data,
            TIMEOUT_DEFAULT,
            false
        )
    }

    companion object {
        private const val TIMEOUT_DEFAULT: Long = 60000
        private const val TASK_KEY = "MediaControlsHeadlessTask"
    }
}