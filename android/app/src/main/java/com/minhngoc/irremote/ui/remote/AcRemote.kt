package com.minhngoc.irremote.ui.remote

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.minhngoc.irremote.R
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WindPower
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minhngoc.irremote.data.IrButton
import com.minhngoc.irremote.ui.LocalDeps
import com.minhngoc.irremote.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A full A/C state, as encoded in the "params" of generated AC buttons. */
data class AcState(
    val power: Boolean = true,
    val mode: String = "cool",
    val temp: Int = 25,
    val fan: String = "auto",
    val swing: Boolean = false,
) {
    /** Matches the "params" written by the pipeline, e.g. power=1,mode=cool,temp=25,fan=auto,swing=0. */
    val key: String
        get() = "power=${if (power) 1 else 0},mode=$mode,temp=${if (mode == "fan") 0 else temp},fan=$fan," +
            "swing=${if (swing) 1 else 0}"

    companion object {
        fun parse(key: String?): AcState? {
            val f = key?.split(",")?.associate { it.substringBefore("=") to it.substringAfter("=") } ?: return null
            return AcState(
                power = f["power"] == "1",
                mode = f["mode"] ?: return null,
                temp = f["temp"]?.toIntOrNull()?.takeIf { it in MIN_TEMP..MAX_TEMP } ?: 25,
                fan = f["fan"] ?: "auto",
                swing = f["swing"] == "1",
            )
        }
    }
}

/** The phone cannot read the unit, so the last state sent is remembered per device. */
object AcMemory {
    private fun prefs(context: Context) = context.getSharedPreferences("ac_state", Context.MODE_PRIVATE)
    fun load(context: Context, deviceId: Long): AcState =
        AcState.parse(prefs(context).getString("device_$deviceId", null)) ?: AcState(power = false)
    fun save(context: Context, deviceId: Long, state: AcState) =
        prefs(context).edit().putString("device_$deviceId", state.key).apply()
}

/** A/C state buttons ("AC" plus per-state "off") of a codeset; empty for non-A/C codesets. */
fun acStateButtons(all: List<IrButton>): List<IrButton> =
    all.filter { it.funcKey == "AC" || (it.funcKey == "POWER_OFF" && it.params?.startsWith("power=0") == true) }

/** The button that best encodes [state]; codesets without per-state "off" frames only have a plain power=0. */
fun acButtonFor(byState: Map<String, IrButton>, state: AcState): IrButton? =
    byState[state.key]
        ?: byState[state.copy(swing = false).key]
        ?: byState[state.copy(swing = false, fan = "auto").key]
        ?: (if (!state.power) byState["power=0"] else null)

@Composable
fun acModeLabel(mode: String): String = MODES.firstOrNull { it.key == mode }?.let { stringResource(it.label) } ?: mode

/** "Cool 25°C, fan auto" for an A/C state key from the database, in the app language. */
@Composable
fun acStateLabel(params: String): String {
    if (params.startsWith("power=0")) return stringResource(R.string.off)
    val state = AcState.parse(params) ?: return params
    val fan = FANS.firstOrNull { it.first == state.fan }?.let { stringResource(it.second) } ?: state.fan
    val base = when {
        state.mode == "fan" -> stringResource(R.string.ac_state_fan, fan)
        else -> stringResource(R.string.ac_state, acModeLabel(state.mode), state.temp, fan)
    }
    return if (state.swing) stringResource(R.string.ac_state_swing, base) else base
}

private const val MIN_TEMP = 16
private const val MAX_TEMP = 30
private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

private data class AcMode(val key: String, @StringRes val label: Int, val icon: ImageVector, val color: Color)

private val MODES = listOf(
    AcMode("cool", R.string.ac_cool, Icons.Filled.AcUnit, Color(0xFF1FA5C9)),
    AcMode("heat", R.string.ac_heat, Icons.Filled.WbSunny, Color(0xFFF08A24)),
    AcMode("dry", R.string.ac_dry, Icons.Filled.WaterDrop, Color(0xFF7A6CF0)),
    AcMode("fan", R.string.ac_fan, Icons.Filled.WindPower, Color(0xFF2FAE73)),
    AcMode("auto", R.string.ac_auto, Icons.Filled.Autorenew, Color(0xFF5B6B7C)),
)
private val FANS = listOf("auto" to R.string.fan_auto, "low" to R.string.fan_low, "medium" to R.string.fan_medium, "high" to R.string.fan_high)

/**
 * State-based A/C control for codesets generated from IRremoteESP8266: each
 * change sends the complete state, like a real A/C remote.
 */
@Composable
fun AcRemote(buttons: List<IrButton>, deviceId: Long) {
    val byState = remember(buttons) { buttons.filter { it.params != null }.associateBy { it.params!! } }
    val ir = LocalDeps.current.ir
    val palette = LocalPalette.current
    val context = LocalContext.current
    var state by remember { mutableStateOf(AcMemory.load(context, deviceId)) }
    val modes = remember(byState) { MODES.filter { m -> byState.keys.any { it.contains("mode=${m.key},") } } }
    val mode = MODES.first { it.key == state.mode }
    val accent by animateColorAsState(if (state.power) mode.color else palette.inkSoft, label = "acAccent")

    fun apply(next: AcState) {
        state = next
        AcMemory.save(context, deviceId, next)
        val button = acButtonFor(byState, next) ?: return Toast.makeText(context, R.string.combo_unsupported, Toast.LENGTH_SHORT).show()
        if (!ir.send(button)) Toast.makeText(context, R.string.no_ir, Toast.LENGTH_SHORT).show()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TemperatureDial(
            state = state,
            modeLabel = stringResource(mode.label),
            accent = accent,
            onTemp = { apply(state.copy(power = true, temp = it)) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StepKey(Icons.Filled.Remove, stringResource(R.string.temp_down)) {
                apply(state.copy(power = true, temp = (state.temp - 1).coerceAtLeast(MIN_TEMP)))
            }
            PowerToggle(on = state.power, onClick = { apply(state.copy(power = !state.power)) })
            StepKey(Icons.Filled.Add, stringResource(R.string.temp_up)) {
                apply(state.copy(power = true, temp = (state.temp + 1).coerceAtMost(MAX_TEMP)))
            }
        }

        // Mode selector: one segmented strip, the active segment takes the mode color.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(palette.groove).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            modes.forEach { m ->
                val selected = state.mode == m.key
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) m.color else Color.Transparent)
                        .clickable { apply(state.copy(power = true, mode = m.key)) }
                        .padding(vertical = 10.dp),
                ) {
                    Icon(m.icon, null, tint = if (selected) Color.White else palette.inkSoft, modifier = Modifier.size(22.dp))
                    Text(stringResource(m.label), style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else palette.inkSoft)
                }
            }
        }

        // Fan speed as rising bars, like the LCD on the physical remote.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.fan_speed), style = MaterialTheme.typography.titleSmall, color = palette.inkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FANS.forEachIndexed { i, (key, label) ->
                    val selected = state.fan == key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) accent.copy(alpha = 0.14f) else palette.groove)
                            .clickable { apply(state.copy(power = true, fan = key)) }
                            .padding(vertical = 10.dp),
                    ) {
                        FanBars(level = i, color = if (selected) accent else palette.inkSoft.copy(alpha = 0.6f))
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = if (selected) accent else palette.inkSoft)
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.groove)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.SwapVert, null, tint = palette.inkSoft)
            Text(stringResource(R.string.swing), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp).weight(1f))
            Switch(
                checked = state.swing,
                onCheckedChange = { apply(state.copy(power = true, swing = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = accent),
            )
        }
    }
}

@Composable
private fun TemperatureDial(state: AcState, modeLabel: String, accent: Color, onTemp: (Int) -> Unit) {
    val palette = LocalPalette.current
    var dragTemp by remember { mutableIntStateOf(-1) }
    val shown = if (dragTemp >= 0) dragTemp else state.temp
    val fraction by animateFloatAsState((shown - MIN_TEMP) / (MAX_TEMP - MIN_TEMP).toFloat(), label = "dial")

    fun tempAt(offset: Offset, size: Float): Int {
        val angle = (atan2(offset.y - size / 2, offset.x - size / 2) * 180 / PI).toFloat()
        var a = (angle - ARC_START + 360f) % 360f
        if (a > ARC_SWEEP) a = if (a > ARC_SWEEP + (360 - ARC_SWEEP) / 2) 0f else ARC_SWEEP
        return (MIN_TEMP + a / ARC_SWEEP * (MAX_TEMP - MIN_TEMP)).roundToInt()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragTemp = tempAt(it, size.width.toFloat()) },
                    onDrag = { change, _ -> dragTemp = tempAt(change.position, size.width.toFloat()) },
                    onDragEnd = { onTemp(dragTemp); dragTemp = -1 },
                    onDragCancel = { dragTemp = -1 },
                )
            }
            .pointerInput(Unit) { detectTapGestures { onTemp(tempAt(it, size.width.toFloat())) } },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val stroke = size.minDimension * 0.075f
            val inset = stroke / 2 + 8f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(palette.groove, ARC_START, ARC_SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(accent, ARC_START, ARC_SWEEP * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // Tick marks for every degree, longer at 20 / 25 / 30.
            val r = arcSize.width / 2
            for (t in MIN_TEMP..MAX_TEMP) {
                val a = Math.toRadians((ARC_START + ARC_SWEEP * (t - MIN_TEMP) / (MAX_TEMP - MIN_TEMP)).toDouble())
                val outer = r - stroke * 0.9f
                val inner = outer - if (t % 5 == 0) stroke * 0.55f else stroke * 0.3f
                drawLine(
                    palette.inkSoft.copy(alpha = if (t % 5 == 0) 0.6f else 0.3f),
                    Offset(center.x + (cos(a) * inner).toFloat(), center.y + (sin(a) * inner).toFloat()),
                    Offset(center.x + (cos(a) * outer).toFloat(), center.y + (sin(a) * outer).toFloat()),
                    strokeWidth = 3f, cap = StrokeCap.Round,
                )
            }
            // Knob at the current temperature.
            val ka = Math.toRadians((ARC_START + ARC_SWEEP * fraction).toDouble())
            val knob = Offset(center.x + (cos(ka) * r).toFloat(), center.y + (sin(ka) * r).toFloat())
            drawCircle(Color.White, stroke * 0.62f, knob)
            drawCircle(accent, stroke * 0.36f, knob)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (state.mode == "fan" && state.power) stringResource(R.string.ac_fan) else "$shown°",
                style = MaterialTheme.typography.displayLarge,
                color = if (state.power) palette.ink else palette.inkSoft,
            )
            Text(
                if (state.power) modeLabel else stringResource(R.string.ac_is_off),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
        }
    }
}

@Composable
private fun PowerToggle(on: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val color by animateColorAsState(if (on) palette.power else Color(0xFF30A46C), label = "power")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(76.dp).shadow(6.dp, CircleShape, spotColor = palette.shadow).clip(CircleShape)
                .background(color).clickable(onClick = onClick),
        ) {
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = stringResource(if (on) R.string.off else R.string.on), tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Text(stringResource(if (on) R.string.off else R.string.on), style = MaterialTheme.typography.labelMedium, color = palette.inkSoft,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun StepKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(bottom = 22.dp).size(width = 92.dp, height = 60.dp)
            .shadow(3.dp, RoundedCornerShape(30.dp), spotColor = palette.shadow).clip(RoundedCornerShape(30.dp))
            .background(palette.key).clickable(onClick = onClick),
    ) { Icon(icon, contentDescription = description, tint = palette.ink, modifier = Modifier.size(28.dp)) }
}

@Composable
private fun FanBars(level: Int, color: Color) {
    if (level == 0) {
        Text("A", style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.height(22.dp))
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(22.dp)) {
        for (i in 1..3) {
            Box(
                Modifier.width(6.dp).fillMaxHeight(i / 3f).clip(RoundedCornerShape(2.dp))
                    .background(if (i <= level) color else color.copy(alpha = 0.2f)),
            )
        }
    }
}
