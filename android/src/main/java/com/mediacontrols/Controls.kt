package com.mediacontrols

enum class Controls(val code: String) {
    PLAY("play"),
    PAUSE("pause"),
    STOP("stop"),
    NEXT("skipToNext"),
    PREVIOUS("skipToPrevious"),
    SEEK("seek"),
    SEEK_BACKWARD("seekBackward"),
    SEEK_FORWARD("seekForward"),
    DUCK("duck"),
    UN_DUCK("unDuck");

    companion object {
        fun fromString(value: String): Controls? {
            return entries.find { it.code == value }
        }
    }
}