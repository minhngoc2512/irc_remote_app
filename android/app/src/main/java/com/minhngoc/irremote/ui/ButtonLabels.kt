package com.minhngoc.irremote.ui

/** Keys that keep sending while held down. */
val HOLDABLE = setOf(
    "VOL_UP", "VOL_DOWN", "CH_UP", "CH_DOWN", "UP", "DOWN", "LEFT", "RIGHT",
    "BRIGHT_UP", "BRIGHT_DOWN", "SPEED_UP", "SPEED_DOWN", "REWIND", "FAST_FORWARD",
)

/** Buttons tried first in the matching wizard, in order. */
val TEST_KEYS = listOf("POWER", "POWER_ON", "VOL_UP", "CH_UP", "MUTE", "TEMP_UP", "MODE", "PLAY", "BRIGHT_UP", "OK", "MENU")
