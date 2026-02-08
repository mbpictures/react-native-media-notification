package com.mediacontrols

import android.content.Intent
import android.os.Bundle
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

class MediaControlsHeadlessTask : HeadlessJsTaskService() {
    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? {
        val extras = intent?.extras
        if (intent == null || extras == null) return null

        val data = Arguments.createMap()
        data.putString("command", intent.parcelable("command"))
        intent.parcelable<Bundle>("data")?.let {
            data.putMap("data", Arguments.fromBundle(it))
        }

        return HeadlessJsTaskConfig(
            TASK_KEY,
            data,
            TIMEOUT_DEFAULT,
            true // fix possible race condition
        )
    }

    companion object {
        private const val TIMEOUT_DEFAULT: Long = 60000
        private const val TASK_KEY = "MediaControlsHeadlessTask"
    }
}