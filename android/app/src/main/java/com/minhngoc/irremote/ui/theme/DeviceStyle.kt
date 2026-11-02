package com.minhngoc.irremote.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WindPower
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.minhngoc.irremote.R
import androidx.compose.ui.graphics.vector.ImageVector

/** Visual identity of a device category (keys match pipeline/irc/normalize.py). */
data class DeviceStyle(val key: String, @StringRes val nameRes: Int, val icon: ImageVector, val accent: Color)

/** The category's name in the app language. */
val DeviceStyle.name: String
    @Composable get() = stringResource(nameRes)

private val STYLES = listOf(
    DeviceStyle("tv", R.string.cat_tv, Icons.Filled.Tv, Color(0xFF2F5BEA)),
    DeviceStyle("ac", R.string.cat_ac, Icons.Filled.AcUnit, Color(0xFF1FA5C9)),
    DeviceStyle("fan", R.string.cat_fan, Icons.Filled.WindPower, Color(0xFF2FAE73)),
    DeviceStyle("stb", R.string.cat_stb, Icons.Filled.Router, Color(0xFFD6407F)),
    DeviceStyle("projector", R.string.cat_projector, Icons.Filled.Theaters, Color(0xFF4652D1)),
    DeviceStyle("soundbar", R.string.cat_soundbar, Icons.Filled.Speaker, Color(0xFF7457E8)),
    DeviceStyle("audio", R.string.cat_audio, Icons.Filled.SpeakerGroup, Color(0xFF8A4FD8)),
    DeviceStyle("player", R.string.cat_player, Icons.Filled.Album, Color(0xFFD6407F)),
    DeviceStyle("light", R.string.cat_light, Icons.Filled.Lightbulb, Color(0xFFE8A800)),
    DeviceStyle("air_purifier", R.string.cat_air_purifier, Icons.Filled.Air, Color(0xFF2E9FB0)),
    DeviceStyle("heater", R.string.cat_heater, Icons.Filled.Whatshot, Color(0xFFE5672E)),
    DeviceStyle("camera", R.string.cat_camera, Icons.Filled.CameraAlt, Color(0xFF5B6B7C)),
    DeviceStyle("monitor", R.string.cat_monitor, Icons.Filled.Monitor, Color(0xFF3D6FD6)),
    DeviceStyle("switcher", R.string.cat_switcher, Icons.Filled.SettingsInputHdmi, Color(0xFF5B6B7C)),
    DeviceStyle("car", R.string.cat_car, Icons.Filled.DirectionsCar, Color(0xFF5B6B7C)),
    DeviceStyle("vacuum", R.string.cat_vacuum, Icons.Filled.CleaningServices, Color(0xFF2FAE73)),
    DeviceStyle("other", R.string.cat_other, Icons.Filled.DevicesOther, Color(0xFF5B6B7C)),
    DeviceStyle("atv", R.string.cat_atv, Icons.Filled.Cast, Color(0xFF1E9E6A)),
    DeviceStyle("webos", R.string.cat_webos, Icons.Filled.Tv, Color(0xFFA50034)),
).associateBy { it.key }

fun deviceStyle(key: String): DeviceStyle = STYLES[key] ?: STYLES.getValue("other")
