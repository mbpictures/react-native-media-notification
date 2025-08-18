package com.mediacontrols

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.CommandButton.ICON_NEXT
import androidx.media3.session.CommandButton.ICON_PREVIOUS
import androidx.media3.session.CommandButton.ICON_SHUFFLE_OFF
import androidx.media3.session.CommandButton.ICON_SHUFFLE_ON
import androidx.media3.session.CommandButton.ICON_SKIP_BACK_15
import androidx.media3.session.CommandButton.ICON_SKIP_FORWARD_15
import androidx.media3.session.SessionCommand

private const val CUSTOM_COMMAND_REWIND_ACTION_ID = "REWIND_15"
private const val CUSTOM_COMMAND_FORWARD_ACTION_ID = "FAST_FWD_15"
private const val CUSTOM_COMMAND_NEXT_ACTION_ID = "SKIP_TO_NEXT"
private const val CUSTOM_COMMAND_PREVIOUS_ACTION_ID = "SKIP_TO_PREVIOUS"
private const val CUSTOM_COMMAND_SHUFFLE_ON_ACTION_ID = "SHUFFLE_ON"
private const val CUSTOM_COMMAND_SHUFFLE_OFF_ACTION_ID = "SHUFFLE_OFF"
private const val CUSTOM_COMMAND_REPEAT_ONE_ACTION_ID = "REPEAT_ONE"
private const val CUSTOM_COMMAND_REPEAT_ALL_ACTION_ID = "REPEAT_ALL"
private const val CUSTOM_COMMAND_REPEAT_OFF_ACTION_ID = "REPEAT_OFF"

@UnstableApi
enum class CustomCommandButton(
    val customAction: String,
    val commandButton: CommandButton,
) {
    PREVIOUS(
        customAction = CUSTOM_COMMAND_PREVIOUS_ACTION_ID,
        commandButton = CommandButton.Builder(ICON_PREVIOUS)
            .setDisplayName("Previous")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_PREVIOUS_ACTION_ID, Bundle()))
            .build(),
    ),
    REWIND(
        customAction = CUSTOM_COMMAND_REWIND_ACTION_ID,
        commandButton = CommandButton.Builder(ICON_SKIP_BACK_15)
            .setDisplayName("Rewind")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_REWIND_ACTION_ID, Bundle()))
            .setSlots(CommandButton.SLOT_BACK, CommandButton.SLOT_OVERFLOW)
            .build(),
    ),
    FORWARD(
        customAction = CUSTOM_COMMAND_FORWARD_ACTION_ID,
        commandButton = CommandButton.Builder(ICON_SKIP_FORWARD_15)
            .setDisplayName("Forward")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_FORWARD_ACTION_ID, Bundle()))
            .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
            .build(),
    ),
    NEXT(
        customAction = CUSTOM_COMMAND_NEXT_ACTION_ID,
        commandButton = CommandButton.Builder(ICON_NEXT)
            .setDisplayName("Next")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_NEXT_ACTION_ID, Bundle()))
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
