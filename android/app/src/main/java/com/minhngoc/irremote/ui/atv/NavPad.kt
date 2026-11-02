package com.minhngoc.irremote.ui.atv

import androidx.compose.ui.res.stringResource
import com.minhngoc.irremote.R
import android.content.Context
import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.minhngoc.irremote.atv.AtvRemote
import com.minhngoc.irremote.data.KeySpec
import com.minhngoc.irremote.ui.remote.IrKey
import com.minhngoc.irremote.ui.theme.LocalPalette
import kotlin.math.abs

enum class NavMode { Keys, Touchpad }

/** The chosen navigation mode, remembered across visits. */
object NavModePref {
    private fun prefs(context: Context) = context.getSharedPreferences("atv_ui", Context.MODE_PRIVATE)
    fun load(context: Context): NavMode = runCatching { NavMode.valueOf(prefs(context).getString("nav", null)!!) }.getOrDefault(NavMode.Keys)
    fun save(context: Context, mode: NavMode) = prefs(context).edit().putString("nav", mode.name).apply()
}

@Composable
fun NavModeSwitch(mode: NavMode, accent: Color, onChange: (NavMode) -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(palette.groove).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(NavMode.Keys to (stringResource(R.string.nav_keys) to Icons.Filled.Gamepad), NavMode.Touchpad to (stringResource(R.string.nav_touchpad) to Icons.Filled.SwipeVertical))
            .forEach { (m, labelIcon) ->
                val selected = m == mode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) accent else Color.Transparent)
                        .clickable { onChange(m) }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(labelIcon.second, null, tint = if (selected) Color.White else palette.inkSoft, modifier = Modifier.size(18.dp))
                    Text(labelIcon.first, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else palette.inkSoft,
                        modifier = Modifier.padding(start = 6.dp))
                }
            }
    }
}

/**
 * Swipe pad like the Google TV app: the protocol has no pointer events, so a
 * drag is turned into D-pad steps (one per [step] of travel along the
 * dominant axis) and a tap presses OK.
 */
@Composable
fun Touchpad(client: AtvRemote, accent: Color) {
    val palette = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    var lastDir by remember { mutableStateOf<ImageVector?>(null) }
    var pulse by remember { mutableIntStateOf(0) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (pulse == 0) return@LaunchedEffect
        flash.snapTo(1f)
        flash.animateTo(0f, tween(450))
    }

    fun press(keyCode: Int, icon: ImageVector?) {
        client.sendKey(keyCode)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        lastDir = icon
        pulse++
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(palette.groove)
            .pointerInput(client) {
                val step = 44.dp.toPx()
                var acc = Offset.Zero
                detectDragGestures(onDragStart = { acc = Offset.Zero }) { change, drag ->
                    change.consume()
                    acc += drag
                    while (abs(acc.x) >= step || abs(acc.y) >= step) {
                        if (abs(acc.x) > abs(acc.y)) {
                            if (acc.x > 0) press(KeyEvent.KEYCODE_DPAD_RIGHT, Icons.Filled.KeyboardArrowRight)
                            else press(KeyEvent.KEYCODE_DPAD_LEFT, Icons.Filled.KeyboardArrowLeft)
                            acc = Offset(acc.x - step * kotlin.math.sign(acc.x), 0f)
                        } else {
                            if (acc.y > 0) press(KeyEvent.KEYCODE_DPAD_DOWN, Icons.Filled.KeyboardArrowDown)
                            else press(KeyEvent.KEYCODE_DPAD_UP, Icons.Filled.KeyboardArrowUp)
                            acc = Offset(0f, acc.y - step * kotlin.math.sign(acc.y))
                        }
                    }
                }
            }
            .pointerInput(client) {
                detectTapGestures(onTap = { press(KeyEvent.KEYCODE_DPAD_CENTER, null) })
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f + 0.5f * flash.value)),
            ) {
                lastDir?.let { Icon(it, null, tint = accent, modifier = Modifier.size(36.dp)) }
                    ?: Text("OK", style = MaterialTheme.typography.titleMedium, color = accent)
            }
            Text(stringResource(R.string.swipe_hint), style = MaterialTheme.typography.bodySmall, color = palette.inkSoft,
                modifier = Modifier.padding(top = 14.dp).alpha(0.9f))
        }
    }
}

/** Horizontal rocker: − on the left, caption in the middle, + on the right. */
@Composable
fun HRocker(caption: String, down: KeySpec, up: KeySpec, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clip(RoundedCornerShape(30.dp)).background(palette.groove).padding(4.dp),
    ) {
        IrKey(down, Modifier.size(52.dp), shape = CircleShape)
        if (icon != null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = caption, tint = palette.inkSoft, modifier = Modifier.size(20.dp))
            }
        } else {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = palette.inkSoft, maxLines = 1, softWrap = false,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        IrKey(up, Modifier.size(52.dp), shape = CircleShape)
    }
}
