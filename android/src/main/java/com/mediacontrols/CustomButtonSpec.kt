package com.mediacontrols

import android.content.Context
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper

const val USER_BUTTON_ACTION_PREFIX = "USER_BTN:"

data class CustomButtonSpec(
    val eventId: String,
    val icon: String,
    val displayName: String?,
) {
    val actionId: String get() = "$USER_BUTTON_ACTION_PREFIX$eventId"

    @UnstableApi
    fun toCommandButton(context: Context): CommandButton? {
        val resId = ResourceDrawableIdHelper.getInstance().getResourceDrawableId(context, icon)
        if (resId == 0) return null
        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(displayName ?: eventId)
            .setIconResId(resId)
            .setSessionCommand(SessionCommand(actionId, Bundle()))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
    }

    @UnstableApi
    fun toSessionCommand(): SessionCommand = SessionCommand(actionId, Bundle())
}
