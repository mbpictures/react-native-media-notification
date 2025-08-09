package com.mediacontrols

import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.CommandButton.ICON_FAST_FORWARD
import androidx.media3.session.CommandButton.ICON_REWIND
import androidx.media3.session.SessionCommand

private const val CUSTOM_COMMAND_REWIND_ACTION_ID = "REWIND_15"
private const val CUSTOM_COMMAND_FORWARD_ACTION_ID = "FAST_FWD_15"

enum class CustomCommandButton(
    val customAction: String,
    val commandButton: CommandButton,
) {
    REWIND(
        customAction = CUSTOM_COMMAND_REWIND_ACTION_ID,
        commandButton = CommandButton.Builder(ICON_REWIND)
            .setDisplayName("Rewind")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_REWIND_ACTION_ID, Bundle()))
            .build(),
    ),
    FORWARD(
        customAction = CUSTOM_COMMAND_FORWARD_ACTION_ID,
        commandButton = CommandButton.Builder(ICON_FAST_FORWARD)
            .setDisplayName("Forward")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_FORWARD_ACTION_ID, Bundle()))
            .setCustomIconResId(androidx.media3.session.R.drawable.media3_icon_fast_forward)
            .build(),
    );
}