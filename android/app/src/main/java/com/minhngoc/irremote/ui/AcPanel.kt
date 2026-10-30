package com.minhngoc.irremote.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
    val key: String
        get() = if (!power) "power=0"
        else "power=1,mode=$mode,temp=${if (mode == "fan") 0 else temp},fan=$fan,swing=${if (swing) 1 else 0}"
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
fun AcPanel(buttons: List<IrButton>) {
    val byState = remember(buttons) { buttons.filter { it.params != null }.associateBy { it.params!! } }
    val ir = LocalDeps.current.ir
    val context = LocalContext.current
    var state by remember { mutableStateOf(AcState()) }

    fun apply(next: AcState) {
        state = next
        val button = byState[next.key]
            ?: byState[next.copy(swing = false).key]
            ?: byState[next.copy(swing = false, fan = "auto").key]
            ?: return Toast.makeText(context, "Bộ mã không hỗ trợ tổ hợp này", Toast.LENGTH_SHORT).show()
        if (!ir.send(button)) Toast.makeText(context, "Không phát được tín hiệu hồng ngoại", Toast.LENGTH_SHORT).show()
    }

    val available = remember(byState) { byState.keys }
    fun supports(mode: String) = available.any { it.contains("mode=$mode,") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { apply(state.copy(power = !state.power)) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = if (state.power) Color(0xFF2E7D32) else Color(0xFFE5484D)),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(72.dp),
                ) { Text(if (state.power) "BẬT" else "TẮT", maxLines = 1) }
                Text(
                    if (state.power && state.mode != "fan") "${state.temp}°C" else if (state.power) "Quạt" else "—",
                    style = MaterialTheme.typography.displayMedium,
                )
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
