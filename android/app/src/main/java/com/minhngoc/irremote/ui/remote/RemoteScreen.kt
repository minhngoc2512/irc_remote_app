package com.minhngoc.irremote.ui.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minhngoc.irremote.data.IrButton
import com.minhngoc.irremote.data.UserDevice
import com.minhngoc.irremote.ui.LocalDeps
import com.minhngoc.irremote.ui.Navigator
import com.minhngoc.irremote.ui.NoEmitterBanner
import com.minhngoc.irremote.ui.loadAsync
import com.minhngoc.irremote.ui.theme.LocalPalette
import com.minhngoc.irremote.ui.theme.deviceStyle

/** First button per normalized function, restricted to the functions a layout draws. */
private class Picked(all: List<IrButton>, keys: Set<String>) {
    val byKey: Map<String, IrButton> = buildMap { all.forEach { b -> b.funcKey?.takeIf { it in keys }?.let { putIfAbsent(it, b) } } }
    val rest: List<IrButton> = all.filter { b -> byKey[b.funcKey]?.id != b.id }
    operator fun get(key: String): IrButton? = byKey[key]
    fun all(vararg keys: String): List<IrButton> = keys.mapNotNull { byKey[it] }
}

private val DIGITS = (0..9).map { "DIGIT_$it" }
private val MEDIA_KEYS = setOf(
    "POWER", "POWER_ON", "POWER_OFF", "INPUT", "MUTE", "VOL_UP", "VOL_DOWN", "CH_UP", "CH_DOWN", "CH_PREV",
    "UP", "DOWN", "LEFT", "RIGHT", "OK", "BACK", "HOME", "MENU", "EXIT", "INFO", "GUIDE", "SETTINGS",
    "SUBTITLE", "AUDIO_TRACK", "ZOOM", "SLEEP", "REWIND", "PLAY", "PLAY_PAUSE", "PAUSE", "STOP", "FAST_FORWARD",
    "PREV", "NEXT", "RECORD", "EJECT", "RED", "GREEN", "YELLOW", "BLUE",
) + DIGITS
private val FAN_KEYS = setOf("POWER", "POWER_ON", "POWER_OFF", "SPEED_UP", "SPEED_DOWN", "FAN_SPEED", "SWING", "TIMER", "MODE", "SLEEP")
private val LIGHT_KEYS = setOf("POWER", "POWER_ON", "POWER_OFF", "BRIGHT_UP", "BRIGHT_DOWN", "TIMER", "MODE", "RED", "GREEN", "BLUE", "YELLOW")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(nav: Navigator, device: UserDevice) {
    val deps = LocalDeps.current
    val palette = LocalPalette.current
    val style = deviceStyle(device.categoryKey)
    val buttons by loadAsync(device.id) {
        val codesetId = device.codesetUid?.let { deps.db.codesetId(it) } ?: device.codesetId
        deps.db.buttons(codesetId)
    }
    var showMore by remember { mutableStateOf(false) }

    Surface(color = palette.room, contentColor = palette.ink, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header: device identity in its category color.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp)) {
            IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") }
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(style.accent), contentAlignment = Alignment.Center) {
                Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${device.brandName} · ${style.name}", style = MaterialTheme.typography.bodySmall, color = palette.inkSoft)
            }
        }

        val all = buttons
        if (all == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = style.accent) }
            return@Column
        }
        val acStates = all.filter { it.funcKey == "AC" || (it.funcKey == "POWER_OFF" && it.params?.startsWith("power=0") == true) }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NoEmitterBanner()
            // The remote body: one molded slab that carries the keys.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(40.dp), ambientColor = palette.shadow, spotColor = palette.shadow)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Brush.verticalGradient(listOf(palette.body, palette.body.copy(alpha = 0.96f))))
                    .padding(horizontal = 18.dp, vertical = 24.dp),
            ) {
                val rest: List<IrButton> = when {
                    acStates.isNotEmpty() -> {
                        AcRemote(acStates, device.id)
                        all - acStates.toSet()
                    }
                    device.categoryKey == "fan" -> FanLayout(all, style.accent)
                    device.categoryKey == "light" -> LightLayout(all, style.accent)
                    device.categoryKey in setOf("soundbar", "audio") -> AudioLayout(all, style.accent)
                    else -> MediaLayout(all, style.accent, mediaFirst = device.categoryKey in setOf("player", "stb"))
                }
                // Few standard keys (e.g. raw A/C captures): show the device's own keys right on the body.
                if (rest.isNotEmpty() && rest.size <= 12 || all.size - rest.size < 4) {
                    LabelKeys(rest)
                } else if (rest.isNotEmpty()) {
                    TextButton(onClick = { showMore = true }) {
                        Icon(Icons.Filled.MoreHoriz, null, tint = style.accent)
                        Text("  Nút khác (${rest.size})", color = style.accent, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (showMore) {
                    ModalBottomSheet(onDismissRequest = { showMore = false }, containerColor = palette.body) {
                        Text("Nút khác", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
                        Text("Các nút riêng của thiết bị, giữ nguyên tên trong bộ mã.", style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSoft, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        Box(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) { LabelKeys(rest) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
}

/** Keys that only have the label from the source database. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelKeys(buttons: List<IrButton>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        buttons.forEach { b ->
            IrKey(b, Modifier.widthIn(min = 76.dp).height(46.dp), shape = RoundedCornerShape(14.dp), elevation = 2.dp) {
                Text(prettyLabel(b.label), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

@Composable
private fun PowerRow(p: Picked, trailing: List<IrButton>) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        p["POWER"]?.let { PowerKey(it) } ?: p["POWER_ON"]?.let { PowerKey(it) }
        Spacer(Modifier.weight(1f))
        (listOfNotNull(p["POWER_ON"].takeIf { p["POWER"] != null }, p["POWER_OFF"]) + trailing).forEach {
            IrKey(it, Modifier.padding(start = 10.dp).size(52.dp), shape = CircleShape)
        }
    }
}

// --- TV, set-top box, player, projector, everything else ------------------------------

@Composable
private fun MediaLayout(all: List<IrButton>, accent: Color, mediaFirst: Boolean): List<IrButton> {
    val p = Picked(all, MEDIA_KEYS)
    var digitsOpen by rememberSaveable { mutableStateOf(false) }
    val palette = LocalPalette.current

    PowerRow(p, p.all("INPUT", "MUTE"))
    if (mediaFirst) MediaKeys(p)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Rocker("Âm lượng", p["VOL_UP"], p["VOL_DOWN"], width = 58.dp, captionIcon = Icons.AutoMirrored.Filled.VolumeUp)
        DPad(p["UP"], p["DOWN"], p["LEFT"], p["RIGHT"], p["OK"], accent, size = 172.dp)
        Rocker("Kênh", p["CH_UP"], p["CH_DOWN"], width = 58.dp)
    }
    KeyRow(p.all("BACK", "HOME", "MENU", "EXIT"))
    if (!mediaFirst) MediaKeys(p)
    p.all("INFO", "GUIDE", "SETTINGS", "SUBTITLE", "AUDIO_TRACK", "ZOOM", "SLEEP").chunked(4).forEach { KeyRow(it, height = 46.dp) }
    KeyRow(p.all("RED", "GREEN", "YELLOW", "BLUE"), height = 20.dp) { KEY_COLORS[it.funcKey] }

    val digits = p.all(*DIGITS.toTypedArray())
    if (digits.size >= 6) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { digitsOpen = !digitsOpen }.padding(8.dp),
        ) {
            Icon(Icons.Filled.Dialpad, null, tint = palette.inkSoft)
            Text("Bàn phím số", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Icon(if (digitsOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = palette.inkSoft)
        }
        AnimatedVisibility(digitsOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row -> KeyRow(row.mapNotNull { p["DIGIT_$it"] }) }
                KeyRow(listOfNotNull(p["CH_PREV"], p["DIGIT_0"]))
            }
        }
    }
    return p.rest + listOfNotNull(p["CH_PREV"].takeIf { digits.size < 6 }, p["PLAY_PAUSE"].takeIf { p["PLAY"] != null })
}

@Composable
private fun MediaKeys(p: Picked) {
    // One play key: a separate play + pause pair wins over the combined toggle.
    val play = if (p["PLAY"] != null) p.all("PLAY", "PAUSE") else p.all("PLAY_PAUSE", "PAUSE")
    KeyRow(listOfNotNull(p["REWIND"]) + play + listOfNotNull(p["STOP"], p["FAST_FORWARD"]))
    KeyRow(p.all("PREV", "RECORD", "EJECT", "NEXT"))
}

// --- Fan ---------------------------------------------------------------------------------

@Composable
private fun FanLayout(all: List<IrButton>, accent: Color): List<IrButton> {
    val p = Picked(all, FAN_KEYS)
    val palette = LocalPalette.current
    val power = p["POWER"] ?: p["POWER_ON"]
    if (power != null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f))) {
            PowerKey(power, size = 104.dp)
        }
    }
    p["POWER_OFF"]?.let { KeyRow(listOfNotNull(p["POWER_ON"].takeIf { p["POWER"] != null }, it)) }
    if (p["SPEED_UP"] != null || p["SPEED_DOWN"] != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            p["SPEED_DOWN"]?.let { IrKey(it, Modifier.size(64.dp), shape = CircleShape) }
            Text("Tốc độ", style = MaterialTheme.typography.titleMedium, color = palette.inkSoft)
            p["SPEED_UP"]?.let { IrKey(it, Modifier.size(64.dp), shape = CircleShape, container = accent, contentColor = Color.White) }
        }
    }
    FeatureTiles(listOf("FAN_SPEED" to "Tốc độ gió", "SWING" to "Xoay", "TIMER" to "Hẹn giờ", "MODE" to "Chế độ", "SLEEP" to "Ngủ"), p, accent)
    return p.rest
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeatureTiles(features: List<Pair<String, String>>, p: Picked, accent: Color) {
    val present = features.mapNotNull { (k, label) -> p[k]?.let { it to label } }
    if (present.isEmpty()) return
    val palette = LocalPalette.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()) {
        present.forEach { (b, label) ->
            IrKey(b, Modifier.size(width = 96.dp, height = 88.dp), shape = RoundedCornerShape(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    funcIcon(b.funcKey)?.let { Icon(it, null, tint = accent, modifier = Modifier.size(26.dp)) }
                        ?: Text(label.take(1), style = MaterialTheme.typography.titleLarge, color = accent)
                    Spacer(Modifier.height(6.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = palette.ink)
                }
            }
        }
    }
}

// --- Light -----------------------------------------------------------------------------

private val COLOR_WORDS = listOf(
    "red" to 0xFFE5484D, "đỏ" to 0xFFE5484D, "green" to 0xFF30A46C, "xanh lá" to 0xFF30A46C, "blue" to 0xFF3E63DD,
    "yellow" to 0xFFF5C400, "vàng" to 0xFFF5C400, "orange" to 0xFFF76B15, "purple" to 0xFF8E4EC6, "violet" to 0xFF8E4EC6,
    "pink" to 0xFFE93D82, "cyan" to 0xFF00A2C7, "white" to 0xFFF5F5F0, "trắng" to 0xFFF5F5F0, "warm" to 0xFFFFC46B,
)

private fun swatchColor(b: IrButton): Color? = KEY_COLORS[b.funcKey]
    ?: COLOR_WORDS.firstOrNull { (w, _) -> b.label.lowercase().replace('_', ' ').contains(w) }?.let { Color(it.second) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LightLayout(all: List<IrButton>, accent: Color): List<IrButton> {
    val p = Picked(all, LIGHT_KEYS)
    val palette = LocalPalette.current
    val on = p["POWER_ON"] ?: p["POWER"]
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        on?.let {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent)))) {
                IrKey(it, Modifier.size(92.dp), shape = CircleShape, container = accent, contentColor = Color.White, elevation = 8.dp) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = it.label, modifier = Modifier.size(40.dp))
                }
            }
        }
        p["POWER_OFF"]?.let { PowerKey(it, size = 64.dp) } ?: p["POWER"].takeIf { p["POWER_ON"] != null }?.let { PowerKey(it) }
    }
    if (p["BRIGHT_UP"] != null || p["BRIGHT_DOWN"] != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(palette.groove).padding(5.dp),
        ) {
            // Small sun dims, big sun brightens: size carries the meaning.
            p["BRIGHT_DOWN"]?.let {
                IrKey(it, Modifier.size(56.dp), shape = CircleShape) { Icon(Icons.Filled.WbSunny, it.label, Modifier.size(16.dp)) }
            }
            Box(Modifier.weight(1f).padding(horizontal = 14.dp).height(8.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.15f), accent))))
            p["BRIGHT_UP"]?.let {
                IrKey(it, Modifier.size(56.dp), shape = CircleShape, contentColor = accent) { Icon(Icons.Filled.WbSunny, it.label, Modifier.size(28.dp)) }
            }
        }
    }
    val swatches = all.filter { swatchColor(it) != null }
    if (swatches.isNotEmpty()) {
        Text("Màu", style = MaterialTheme.typography.titleSmall, color = palette.inkSoft, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            swatches.forEach { b ->
                IrKey(b, Modifier.size(52.dp), shape = CircleShape, container = swatchColor(b)!!, elevation = 4.dp) {}
            }
        }
    }
    FeatureTiles(listOf("MODE" to "Chế độ", "TIMER" to "Hẹn giờ"), p, accent)
    return p.rest - swatches.toSet()
}

// --- Speakers and amplifiers -------------------------------------------------------------

@Composable
private fun AudioLayout(all: List<IrButton>, accent: Color): List<IrButton> {
    val p = Picked(all, MEDIA_KEYS)
    val palette = LocalPalette.current
    PowerRow(p, p.all("INPUT", "MUTE"))
    // Volume "knob": a big recessed disc split into + and −.
    if (p["VOL_UP"] != null || p["VOL_DOWN"] != null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp).clip(CircleShape).background(palette.groove).padding(10.dp)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p["VOL_UP"]?.let {
                    IrKey(it, Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
                        container = accent, contentColor = Color.White) { KeyFace(it, iconSize = 34.dp) }
                }
                p["VOL_DOWN"]?.let {
                    IrKey(it, Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 100.dp, bottomEnd = 100.dp)) {
                        KeyFace(it, iconSize = 34.dp)
                    }
                }
            }
            Box(Modifier.size(width = 90.dp, height = 26.dp).clip(CircleShape).background(palette.body), contentAlignment = Alignment.Center) {
                Text("Âm lượng", style = MaterialTheme.typography.labelSmall, color = palette.inkSoft)
            }
        }
    }
    DPad(p["UP"], p["DOWN"], p["LEFT"], p["RIGHT"], p["OK"], accent, size = 160.dp)
    MediaKeys(p)
    KeyRow(p.all("BACK", "MENU", "SETTINGS", "SLEEP"), height = 46.dp)
    return p.rest + p.all("CH_UP", "CH_DOWN") + p.all(*DIGITS.toTypedArray()) +
        p.all("HOME", "EXIT", "INFO", "GUIDE", "SUBTITLE", "AUDIO_TRACK", "ZOOM", "CH_PREV", "RED", "GREEN", "YELLOW", "BLUE")
}
