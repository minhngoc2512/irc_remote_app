package com.minhngoc.irremote.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Visual identity of a device category (keys match pipeline/irc/normalize.py). */
data class DeviceStyle(val key: String, val name: String, val icon: ImageVector, val accent: Color)

private val STYLES = listOf(
    DeviceStyle("tv", "TV", Icons.Filled.Tv, Color(0xFF2F5BEA)),
    DeviceStyle("ac", "Điều hòa", Icons.Filled.AcUnit, Color(0xFF1FA5C9)),
    DeviceStyle("fan", "Quạt", Icons.Filled.WindPower, Color(0xFF2FAE73)),
    DeviceStyle("stb", "Đầu thu / TV box", Icons.Filled.Router, Color(0xFFD6407F)),
    DeviceStyle("projector", "Máy chiếu", Icons.Filled.Theaters, Color(0xFF4652D1)),
    DeviceStyle("soundbar", "Loa / Soundbar", Icons.Filled.Speaker, Color(0xFF7457E8)),
    DeviceStyle("audio", "Amply / Receiver", Icons.Filled.SpeakerGroup, Color(0xFF8A4FD8)),
    DeviceStyle("player", "Đầu đĩa / Media", Icons.Filled.Album, Color(0xFFD6407F)),
    DeviceStyle("light", "Đèn", Icons.Filled.Lightbulb, Color(0xFFE8A800)),
    DeviceStyle("air_purifier", "Lọc khí / Tạo ẩm", Icons.Filled.Air, Color(0xFF2E9FB0)),
    DeviceStyle("heater", "Máy sưởi", Icons.Filled.Whatshot, Color(0xFFE5672E)),
    DeviceStyle("camera", "Camera", Icons.Filled.CameraAlt, Color(0xFF5B6B7C)),
    DeviceStyle("monitor", "Màn hình", Icons.Filled.Monitor, Color(0xFF3D6FD6)),
    DeviceStyle("switcher", "Bộ chuyển tín hiệu", Icons.Filled.SettingsInputHdmi, Color(0xFF5B6B7C)),
    DeviceStyle("car", "Âm thanh ô tô", Icons.Filled.DirectionsCar, Color(0xFF5B6B7C)),
    DeviceStyle("vacuum", "Robot hút bụi", Icons.Filled.CleaningServices, Color(0xFF2FAE73)),
    DeviceStyle("other", "Khác", Icons.Filled.DevicesOther, Color(0xFF5B6B7C)),
    DeviceStyle("atv", "Android TV", Icons.Filled.Cast, Color(0xFF1E9E6A)),
    DeviceStyle("webos", "TV LG webOS", Icons.Filled.Tv, Color(0xFFA50034)),
).associateBy { it.key }

fun deviceStyle(key: String): DeviceStyle = STYLES[key] ?: STYLES.getValue("other")
