package com.mediacontrols

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import androidx.media3.common.util.UnstableApi
import com.google.common.collect.ImmutableList

@UnstableApi
class MediaNotificationProvider(private val context: Context) : MediaNotification.Provider {

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {

        // Create PendingIntent to open the app when notification is clicked
        val contentIntent = createContentIntent()

        // Get the default notification from the session
        val defaultProvider = androidx.media3.session.DefaultMediaNotificationProvider(context)
        val notification = defaultProvider.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            onNotificationChangedCallback
        )

        // Modify the notification to add the content intent
        val modifiedNotification = notification.notification.apply {
            contentIntent?.let { pendingIntent ->
                this.contentIntent = pendingIntent
            }
        }

        return MediaNotification(notification.notificationId, modifiedNotification)
    }

    private fun createContentIntent(): PendingIntent? {
        return try {
            // Get the launcher activity from the host app
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.let {
                PendingIntent.getActivity(
                    context,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaNotificationProvider", "Failed to create content intent", e)
            null
        }
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean {
        // Handle custom commands if needed
        return false
    }
}
