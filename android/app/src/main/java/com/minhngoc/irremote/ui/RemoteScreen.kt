package com.minhngoc.irremote.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minhngoc.irremote.data.IrButton
import com.minhngoc.irremote.data.UserDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val KEY_COLORS = mapOf(
    "RED" to Color(0xFFD32F2F), "GREEN" to Color(0xFF388E3C),
    "YELLOW" to Color(0xFFFBC02D), "BLUE" to Color(0xFF1976D2),
)

/** One remote button: tap sends a press, holding a [HOLDABLE] key keeps repeating. */
@Composable
fun RemoteKey(
    button: IrButton,
    modifier: Modifier = Modifier,
    text: String = FUNC_LABELS[button.funcKey] ?: button.label,
    shape: Shape = RoundedCornerShape(14.dp),
    container: Color = KEY_COLORS[button.funcKey] ?: MaterialTheme.colorScheme.secondaryContainer,
) {
    val ir = LocalDeps.current.ir
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val holdable = button.funcKey in HOLDABLE

    Surface(
        shape = shape,
        color = if (pressed) MaterialTheme.colorScheme.primary else container,
        contentColor = if (pressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.pointerInput(button.id) {
            // Send on release so that scrolling over a key does not fire it; held
            // HOLDABLE keys start sending (and repeating) after a short delay.
            detectTapGestures(onPress = {
                pressed = true
                var sent = false
                fun sendOnce() {
                    sent = true
                    if (!ir.send(button)) {
                        Toast.makeText(context, "Không phát được tín hiệu hồng ngoại", Toast.LENGTH_SHORT).show()
                    }
                }
                val repeater = if (holdable) scope.launch {
                    delay(400)
                    sendOnce()
                    while (true) {
                        delay(110)
                        ir.sendRepeat(button)
                    }
                } else null
                val released = tryAwaitRelease()
                repeater?.cancel()
                if (released && !sent) sendOnce()
                pressed = false
            })
        },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteScreen(nav: Navigator, device: UserDevice) {
    val deps = LocalDeps.current
    val buttons by loadAsync(device.codesetId) { deps.db.buttons(device.codesetId) }

    AppScaffold(device.name, nav) { padding ->
        val all = buttons ?: return@AppScaffold Loading(padding)
        val byKey = LinkedHashMap<String, IrButton>()
        all.forEach { b -> b.funcKey?.let { byKey.putIfAbsent(it, b) } }
        val used = mutableSetOf<Long>()
        fun key(k: String): IrButton? = byKey[k]?.also { used += it.id }

        @Composable
        fun KeyRow(keys: List<String>) {
            val present = keys.mapNotNull { key(it) }
            if (present.isEmpty()) return
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                present.forEach { RemoteKey(it, Modifier.weight(1f).height(52.dp)) }
            }
        }

        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NoEmitterBanner()
            val acStates = all.filter { it.funcKey == "AC" || (it.funcKey == "POWER_OFF" && it.params == "power=0") }
            if (acStates.isNotEmpty()) {
                AcPanel(acStates)
                used += acStates.map { it.id }
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                key("POWER")?.let {
                    RemoteKey(it, Modifier.size(64.dp), shape = CircleShape, container = Color(0xFFE5484D))
                }
                KeyRow(listOf("POWER_ON", "POWER_OFF", "INPUT", "MUTE"))
            }

            if (device.categoryKey == "ac" || byKey.containsKey("TEMP_UP")) {
                Rocker("Nhiệt độ", key("TEMP_UP"), key("TEMP_DOWN"))
                KeyRow(listOf("MODE", "FAN_SPEED", "SWING", "TIMER"))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Rocker("Âm lượng", key("VOL_UP"), key("VOL_DOWN"))
                DPad(key("UP"), key("DOWN"), key("LEFT"), key("RIGHT"), key("OK"))
                Rocker("Kênh", key("CH_UP"), key("CH_DOWN"))
            }
            Rocker("Độ sáng", key("BRIGHT_UP"), key("BRIGHT_DOWN"))
            Rocker("Tốc độ", key("SPEED_UP"), key("SPEED_DOWN"))

            KeyRow(listOf("BACK", "HOME", "MENU", "EXIT"))
            KeyRow(listOf("INFO", "GUIDE", "SETTINGS", "SUBTITLE", "AUDIO_TRACK"))
            KeyRow(listOf("REWIND", "PLAY", "PLAY_PAUSE", "PAUSE", "STOP", "FAST_FORWARD"))
            KeyRow(listOf("PREV", "RECORD", "EJECT", "NEXT"))
            KeyRow(listOf("RED", "GREEN", "YELLOW", "BLUE"))

            if ((0..9).count { byKey.containsKey("DIGIT_$it") } >= 6) {
                for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) KeyRow(row.map { "DIGIT_$it" })
                KeyRow(listOf("CH_PREV", "DIGIT_0", "SLEEP"))
            }

            val rest = all.filter { it.id !in used }
            if (rest.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Các nút khác", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rest.forEach { RemoteKey(it, Modifier.widthIn(min = 72.dp).height(48.dp), text = it.label) }
                }
            }
        }
    }
}

@Composable
private fun Rocker(title: String, up: IrButton?, down: IrButton?) {
    if (up == null && down == null) return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(36.dp)),
    ) {
        up?.let { RemoteKey(it, Modifier.size(72.dp), shape = CircleShape) }
        Text(title, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 6.dp))
        down?.let { RemoteKey(it, Modifier.size(72.dp), shape = CircleShape) }
    }
}

@Composable
private fun DPad(up: IrButton?, down: IrButton?, left: IrButton?, right: IrButton?, ok: IrButton?) {
    if (listOfNotNull(up, down, left, right, ok).isEmpty()) return
    val cell = Modifier.size(52.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        up?.let { RemoteKey(it, cell) } ?: Spacer(cell)
        Row(verticalAlignment = Alignment.CenterVertically) {
            left?.let { RemoteKey(it, cell) } ?: Spacer(cell)
            ok?.let { RemoteKey(it, cell, shape = CircleShape) } ?: Spacer(cell)
            right?.let { RemoteKey(it, cell) } ?: Spacer(cell)
        }
        down?.let { RemoteKey(it, cell) } ?: Spacer(cell)
    }
}
