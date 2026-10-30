package com.minhngoc.irremote.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minhngoc.irremote.data.IrButton

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
                temp = f["temp"]?.toIntOrNull()?.takeIf { it in 16..30 } ?: 25,
                fan = f["fan"] ?: "auto",
                swing = f["swing"] == "1",
            )
        }
    }
}

private val MODES = listOf("cool" to "Lạnh", "heat" to "Sưởi", "dry" to "Hút ẩm", "fan" to "Quạt", "auto" to "Tự động")
private val FANS = listOf("auto" to "Tự động", "low" to "Thấp", "medium" to "Vừa", "high" to "Cao")

/**
 * State-based A/C control for codesets generated from IRremoteESP8266: each
 * change sends the complete state, like a real A/C remote. Combinations the
 * codeset lacks fall back to the closest available one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AcPanel(buttons: List<IrButton>, deviceId: Long) {
    val byState = remember(buttons) { buttons.filter { it.params != null }.associateBy { it.params!! } }
    val ir = LocalDeps.current.ir
    val context = LocalContext.current
    // The phone cannot read the A/C, so remember the last state sent to this device.
    val prefs = remember { context.getSharedPreferences("ac_state", Context.MODE_PRIVATE) }
    var state by remember { mutableStateOf(AcState.parse(prefs.getString("device_$deviceId", null)) ?: AcState(power = false)) }

    fun apply(next: AcState) {
        state = next
        prefs.edit().putString("device_$deviceId", next.key).apply()
        // Codesets without per-state "off" frames only have a plain power=0.
        val button = byState[next.key]
            ?: byState[next.copy(swing = false).key]
            ?: byState[next.copy(swing = false, fan = "auto").key]
            ?: (if (!next.power) byState["power=0"] else null)
            ?: return Toast.makeText(context, "Bộ mã không hỗ trợ tổ hợp này", Toast.LENGTH_SHORT).show()
        if (!ir.send(button)) Toast.makeText(context, "Không phát được tín hiệu hồng ngoại", Toast.LENGTH_SHORT).show()
    }

    val available = remember(byState) { byState.keys }
    fun supports(mode: String) = available.any { it.contains("mode=$mode,") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                when {
                    !state.power -> "Đã tắt · ${state.temp}°C"
                    state.mode == "fan" -> "Quạt"
                    else -> "${state.temp}°C"
                },
                style = MaterialTheme.typography.displayMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { apply(state.copy(power = true)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("BẬT") }
                Button(
                    onClick = { apply(state.copy(power = false)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5484D)),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("TẮT") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { apply(state.copy(power = true, temp = (state.temp - 1).coerceAtLeast(16))) },
                    modifier = Modifier.weight(1f),
                ) { Text("−  Giảm") }
                FilledTonalButton(
                    onClick = { apply(state.copy(power = true, temp = (state.temp + 1).coerceAtMost(30))) },
                    modifier = Modifier.weight(1f),
                ) { Text("+  Tăng") }
            }

            Text("Chế độ", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MODES.filter { supports(it.first) }.forEach { (key, label) ->
                    FilterChip(
                        selected = state.mode == key,
                        onClick = { apply(state.copy(power = true, mode = key)) },
                        label = { Text(label) },
                    )
                }
            }

            Text("Tốc độ quạt", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FANS.forEach { (key, label) ->
                    FilterChip(
                        selected = state.fan == key,
                        onClick = { apply(state.copy(power = true, fan = key)) },
                        label = { Text(label) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Đảo gió", Modifier.weight(1f))
                Switch(checked = state.swing, onCheckedChange = { apply(state.copy(power = true, swing = it)) })
            }
        }
    }
}
