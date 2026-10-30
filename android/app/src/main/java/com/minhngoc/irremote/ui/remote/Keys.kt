package com.minhngoc.irremote.ui.remote

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minhngoc.irremote.data.IrButton
import com.minhngoc.irremote.data.KeySpec
import com.minhngoc.irremote.ui.HOLDABLE
import com.minhngoc.irremote.ui.LocalDeps
import com.minhngoc.irremote.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Where key presses go. Null (the default) means the IR blaster; network
 * remotes provide their own sender for the keys under them.
 */
interface KeySender {
    fun press(key: KeySpec): Boolean
    fun repeat(key: KeySpec)
}

val LocalKeySender = androidx.compose.runtime.staticCompositionLocalOf<KeySender?> { null }

/** Glyph for a normalized button function; null means "show the label". */
fun funcIcon(key: String?): ImageVector? = when (key) {
    "POWER", "POWER_ON", "POWER_OFF" -> Icons.Filled.PowerSettingsNew
    "VOL_UP", "CH_UP", "TEMP_UP", "SPEED_UP" -> Icons.Filled.Add
    "VOL_DOWN", "CH_DOWN", "TEMP_DOWN", "SPEED_DOWN" -> Icons.Filled.Remove
    "MUTE" -> Icons.AutoMirrored.Filled.VolumeOff
    "CH_PREV" -> Icons.Filled.History
    "UP" -> Icons.Filled.KeyboardArrowUp
    "DOWN" -> Icons.Filled.KeyboardArrowDown
    "LEFT" -> Icons.Filled.KeyboardArrowLeft
    "RIGHT" -> Icons.Filled.KeyboardArrowRight
    "BACK" -> Icons.AutoMirrored.Filled.ArrowBack
    "EXIT" -> Icons.Filled.Close
    "MENU" -> Icons.Filled.Menu
    "HOME" -> Icons.Filled.Home
    "INFO" -> Icons.Filled.Info
    "GUIDE" -> Icons.Filled.ListAlt
    "SETTINGS" -> Icons.Filled.Settings
    "INPUT" -> Icons.Filled.Input
    "PLAY", "PLAY_PAUSE" -> Icons.Filled.PlayArrow
    "PAUSE" -> Icons.Filled.Pause
    "STOP" -> Icons.Filled.Stop
    "REWIND" -> Icons.Filled.FastRewind
    "FAST_FORWARD" -> Icons.Filled.FastForward
    "PREV" -> Icons.Filled.SkipPrevious
    "NEXT" -> Icons.Filled.SkipNext
    "RECORD" -> Icons.Filled.FiberManualRecord
    "EJECT" -> Icons.Filled.Eject
    "SUBTITLE" -> Icons.Filled.Subtitles
    "AUDIO_TRACK" -> Icons.Filled.Audiotrack
    "SLEEP" -> Icons.Filled.Bedtime
    "TIMER" -> Icons.Filled.Timer
    "SWING" -> Icons.Filled.SwapVert
    "BRIGHT_UP" -> Icons.Filled.Brightness5
    "BRIGHT_DOWN" -> Icons.Filled.BrightnessLow
    "ZOOM" -> Icons.Filled.AspectRatio
    else -> null
}

/** "KEY_VOL_UP" / "vol_up" -> "Vol up": source labels made readable. */
fun prettyLabel(label: String): String {
    val t = label.replace(Regex("^(KEY|BTN|IR)_", RegexOption.IGNORE_CASE), "").replace('_', ' ').replace(Regex("\\s+"), " ").trim()
    if (t.isEmpty()) return label
    val lower = if (t.any { it.isLowerCase() }) t else t.lowercase()
    return lower.replaceFirstChar { it.uppercase() }
}

/** Short text for keys whose function is better read than drawn. */
private val FUNC_TEXT = mapOf(
    "OK" to "OK", "MODE" to "Chế độ", "FAN_SPEED" to "Gió", "POWER_ON" to "Bật", "POWER_OFF" to "Tắt",
) + (0..9).associate { "DIGIT_$it" to "$it" }

val KEY_COLORS = mapOf(
    "RED" to Color(0xFFE5484D), "GREEN" to Color(0xFF30A46C),
    "YELLOW" to Color(0xFFF5C400), "BLUE" to Color(0xFF3E63DD),
)

/**
 * A raised key on the remote body. Sends on release so that scrolling over a
 * key does not fire it; held [HOLDABLE] keys start repeating after 400 ms.
 */
@Composable
fun IrKey(
    button: KeySpec,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    container: Color = LocalPalette.current.key,
    contentColor: Color = LocalPalette.current.ink,
    elevation: Dp = 3.dp,
    content: @Composable () -> Unit = { KeyFace(button) },
) {
    val ir = LocalDeps.current.ir
    val sender = LocalKeySender.current
    val palette = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(stiffness = 900f), label = "keyScale")
    val holdable = button.funcKey in HOLDABLE

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .shadow(if (pressed) 0.dp else elevation, shape, ambientColor = palette.shadow, spotColor = palette.shadow)
            .clip(shape)
            .background(if (pressed) lerpPressed(container, palette.keyPressed) else container)
            .pointerInput(button.id) {
                detectTapGestures(onPress = {
                    pressed = true
                    var sent = false
                    fun sendOnce() {
                        sent = true
                        if (!(sender?.press(button) ?: ir.send(button as IrButton))) {
                            val why = if (sender != null) "Chưa kết nối được với TV" else "Điện thoại không có cổng hồng ngoại"
                            Toast.makeText(context, why, Toast.LENGTH_SHORT).show()
                        }
                    }
                    val repeater = if (holdable) scope.launch {
                        delay(400)
                        sendOnce()
                        while (true) {
                            delay(110)
                            sender?.repeat(button) ?: ir.sendRepeat(button as IrButton)
                        }
                    } else null
                    val released = tryAwaitRelease()
                    repeater?.cancel()
                    if (released && !sent) sendOnce()
                    pressed = false
                })
            },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) { content() }
    }
}

private fun lerpPressed(container: Color, pressed: Color): Color =
    if (container.alpha < 1f || container == pressed) pressed else androidx.compose.ui.graphics.lerp(container, Color.Black, 0.12f)

@Composable
fun KeyFace(button: KeySpec, iconSize: Dp = 24.dp) {
    val icon = funcIcon(button.funcKey)
    val text = FUNC_TEXT[button.funcKey]
    when {
        text != null -> Text(text, style = MaterialTheme.typography.titleMedium)
        icon != null -> Icon(icon, contentDescription = button.label, modifier = Modifier.size(iconSize))
        else -> Text(
            prettyLabel(button.label),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/** Round red power key, the one element every remote shares. */
@Composable
fun PowerKey(button: KeySpec, size: Dp = 64.dp) {
    val palette = LocalPalette.current
    IrKey(button, Modifier.size(size), shape = CircleShape, container = palette.power, contentColor = Color.White, elevation = 6.dp) {
        Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Nguồn", modifier = Modifier.size(size * 0.42f))
    }
}

/** Vertical pill rocker in a recessed track: + on top, − below, caption between. */
@Composable
fun Rocker(caption: String, up: KeySpec?, down: KeySpec?, width: Dp = 64.dp, captionIcon: ImageVector? = null) {
    if (up == null && down == null) return
    val palette = LocalPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(width / 2))
            .background(palette.groove)
            .padding(5.dp),
    ) {
        val keyShape = RoundedCornerShape(topStart = width / 2, topEnd = width / 2, bottomStart = 10.dp, bottomEnd = 10.dp)
        up?.let { IrKey(it, Modifier.fillMaxWidth().height(width), shape = keyShape) }
        if (captionIcon != null) {
            Icon(captionIcon, contentDescription = caption, tint = palette.inkSoft, modifier = Modifier.padding(vertical = 8.dp).size(18.dp))
        } else {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = palette.inkSoft, maxLines = 1, softWrap = false,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        down?.let {
            IrKey(it, Modifier.fillMaxWidth().height(width),
                shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = width / 2, bottomEnd = width / 2))
        }
    }
}

/** Circular navigation pad: four arrow keys around an accent-colored OK. */
@Composable
fun DPad(up: KeySpec?, down: KeySpec?, left: KeySpec?, right: KeySpec?, ok: KeySpec?, accent: Color, size: Dp = 196.dp) {
    if (listOfNotNull(up, down, left, right, ok).isEmpty()) return
    val palette = LocalPalette.current
    val arm = size * 0.30f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(palette.groove)
            drawCircle(palette.shadow.copy(alpha = 0.10f), radius = this.size.minDimension / 2 - 1f, style = Stroke(2f),
                center = Offset(center.x, center.y + 1.5f))
        }
        val arrow = Modifier.size(arm)
        up?.let { IrKey(it, arrow.align(Alignment.TopCenter).padding(top = 6.dp), shape = CircleShape, elevation = 0.dp, container = Color.Transparent) }
        down?.let { IrKey(it, arrow.align(Alignment.BottomCenter).padding(bottom = 6.dp), shape = CircleShape, elevation = 0.dp, container = Color.Transparent) }
        left?.let { IrKey(it, arrow.align(Alignment.CenterStart).padding(start = 6.dp), shape = CircleShape, elevation = 0.dp, container = Color.Transparent) }
        right?.let { IrKey(it, arrow.align(Alignment.CenterEnd).padding(end = 6.dp), shape = CircleShape, elevation = 0.dp, container = Color.Transparent) }
        ok?.let {
            IrKey(it, Modifier.size(size * 0.40f), shape = CircleShape, container = accent, contentColor = Color.White, elevation = 5.dp)
        }
    }
}

/** A row of equally wide small keys; renders nothing if none of them exist. */
@Composable
fun KeyRow(buttons: List<KeySpec>, height: Dp = 52.dp, keyContainer: (KeySpec) -> Color? = { null }) {
    if (buttons.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        buttons.forEach { b ->
            val tint = keyContainer(b)
            if (tint != null) {
                IrKey(b, Modifier.weight(1f).height(height), container = tint, contentColor = Color.White)
            } else {
                IrKey(b, Modifier.weight(1f).height(height))
            }
        }
    }
}
