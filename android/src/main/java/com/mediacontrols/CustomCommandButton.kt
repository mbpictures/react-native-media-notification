package com.mediacontrols

import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.CommandButton.ICON_FAST_FORWARD
import androidx.media3.session.CommandButton.ICON_REWIND
import androidx.media3.session.CommandButton.ICON_SHUFFLE_OFF
import androidx.media3.session.CommandButton.ICON_SHUFFLE_ON
import androidx.media3.session.SessionCommand

private const val CUSTOM_COMMAND_REWIND_ACTION_ID = "REWIND_15"
private const val CUSTOM_COMMAND_FORWARD_ACTION_ID = "FAST_FWD_15"
private const val CUSTOM_COMMAND_SHUFFLE_ON_ACTION_ID = "SHUFFLE_ON"
private const val CUSTOM_COMMAND_SHUFFLE_OFF_ACTION_ID = "SHUFFLE_OFF"
private const val CUSTOM_COMMAND_REPEAT_ONE_ACTION_ID = "REPEAT_ONE"
private const val CUSTOM_COMMAND_REPEAT_ALL_ACTION_ID = "REPEAT_ALL"
private const val CUSTOM_COMMAND_REPEAT_OFF_ACTION_ID = "REPEAT_OFF"

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
            .build(),
    ),
    SHUFFLE_ON(
        customAction = CUSTOM_COMMAND_SHUFFLE_ON_ACTION_ID,
        commandButton = CommandButton.Builder(ICON_SHUFFLE_ON)
            .setDisplayName("ShuffleOn")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SHUFFLE_ON_ACTION_ID, Bundle()))
            .build(),
    ),
    SHUFFLE_OFF(
        customAction = CUSTOM_COMMAND_SHUFFLE_OFF_ACTION_ID,
        commandButton = CommandButton.Builder(ICON_SHUFFLE_OFF)
            .setDisplayName("ShuffleOn")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SHUFFLE_OFF_ACTION_ID, Bundle()))
            .build(),
    ),
    REPEAT_ONE(
        customAction = CUSTOM_COMMAND_REPEAT_ONE_ACTION_ID,
        commandButton = CommandButton.Builder(CommandButton.ICON_REPEAT_ONE)
            .setDisplayName("Repeat One")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_REPEAT_ONE_ACTION_ID, Bundle()))
            .build(),
    ),
    REPEAT_ALL(
        customAction = CUSTOM_COMMAND_REPEAT_ALL_ACTION_ID,
        commandButton = CommandButton.Builder(CommandButton.ICON_REPEAT_ALL)
            .setDisplayName("Repeat All")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_REPEAT_ALL_ACTION_ID, Bundle()))
            .build(),
    ),
    REPEAT_OFF(
        customAction = CUSTOM_COMMAND_REPEAT_OFF_ACTION_ID,
        commandButton = CommandButton.Builder(CommandButton.ICON_REPEAT_OFF)
            .setDisplayName("Repeat Off")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_REPEAT_OFF_ACTION_ID, Bundle()))
            .build(),
    ),;
}
