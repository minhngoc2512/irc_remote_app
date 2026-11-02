package com.minhngoc.irremote.ui.webos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minhngoc.irremote.data.KeySpec
import com.minhngoc.irremote.data.UserDevice
import com.minhngoc.irremote.ui.AppScaffold
import com.minhngoc.irremote.ui.LocalDeps
import com.minhngoc.irremote.ui.Navigator
import com.minhngoc.irremote.ui.Screen
import com.minhngoc.irremote.ui.atv.DictateButton
import com.minhngoc.irremote.ui.atv.HRocker
import com.minhngoc.irremote.ui.atv.LatencyChip
import com.minhngoc.irremote.ui.atv.NavMode
import com.minhngoc.irremote.ui.atv.NavModePref
import com.minhngoc.irremote.ui.atv.NavModeSwitch
import com.minhngoc.irremote.ui.remote.DPad
import com.minhngoc.irremote.ui.remote.IrKey
import com.minhngoc.irremote.ui.remote.KeyRow
import com.minhngoc.irremote.ui.remote.KeySender
import com.minhngoc.irremote.ui.remote.LocalKeySender
import com.minhngoc.irremote.ui.remote.PowerKey
import com.minhngoc.irremote.ui.remote.RemoteFrame
import com.minhngoc.irremote.ui.theme.LocalPalette
import com.minhngoc.irremote.ui.theme.deviceStyle
import com.minhngoc.irremote.webos.FoundWebOs
import com.minhngoc.irremote.webos.PrefsWebOsStore
import com.minhngoc.irremote.webos.WebOsClient
import com.minhngoc.irremote.webos.WebOsDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A key of the LG remote: a normalized function plus the webOS button name sent on the pointer socket. */
data class WebOsKey(override val funcKey: String, val button: String, override val label: String = funcKey) : KeySpec {
    override val id: Long get() = button.hashCode().toLong()
}

private val KEYS = listOf(
    WebOsKey("POWER", "POWER", "Nguồn"), WebOsKey("MUTE", "MUTE", "Tắt tiếng"),
    WebOsKey("VOL_UP", "VOLUMEUP"), WebOsKey("VOL_DOWN", "VOLUMEDOWN"),
    WebOsKey("CH_UP", "CHANNELUP"), WebOsKey("CH_DOWN", "CHANNELDOWN"),
    WebOsKey("UP", "UP"), WebOsKey("DOWN", "DOWN"), WebOsKey("LEFT", "LEFT"), WebOsKey("RIGHT", "RIGHT"),
    WebOsKey("OK", "ENTER"), WebOsKey("BACK", "BACK", "Quay lại"), WebOsKey("HOME", "HOME", "Màn hình chính"),
    WebOsKey("SETTINGS", "MENU", "Cài đặt nhanh"), WebOsKey("EXIT", "EXIT", "Thoát"),
    WebOsKey("REWIND", "REWIND"), WebOsKey("PLAY", "PLAY"), WebOsKey("PAUSE", "PAUSE"),
    WebOsKey("STOP", "STOP"), WebOsKey("FAST_FORWARD", "FASTFORWARD"),
    WebOsKey("RED", "RED"), WebOsKey("GREEN", "GREEN"), WebOsKey("YELLOW", "YELLOW"), WebOsKey("BLUE", "BLUE"),
).associateBy { it.funcKey }

fun webOsClient(context: android.content.Context, host: String) = WebOsClient.of(host, PrefsWebOsStore(context))

// --- Add: find the TV, accept the prompt on it -----------------------------------------------

@Composable
fun WebOsAddScreen(nav: Navigator) {
    val deps = LocalDeps.current
    val context = LocalContext.current
    val palette = LocalPalette.current
    val style = deviceStyle("webos")
    val found by remember { WebOsDiscovery.scan(context) }.collectAsState(initial = emptyList())
    var picked by remember { mutableStateOf<FoundWebOs?>(null) }
    val saved by com.minhngoc.irremote.ui.loadAsync(Unit) { deps.devices.all() }
    var manual by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AppScaffold("TV LG (webOS) qua Wi-Fi", nav, subtitle = "Điện thoại và TV cần cùng mạng Wi-Fi", style = style) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tv = picked
            if (tv == null) {
                Text("TV trong mạng nhà bạn", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                if (found.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = style.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text("Đang tìm… Hãy bật TV.", style = MaterialTheme.typography.bodySmall, color = palette.inkSoft,
                            modifier = Modifier.padding(start = 12.dp))
                    }
                }
                found.forEach { f ->
                    // Already saved: open its remote instead of adding a second copy.
                    val existing = saved?.firstOrNull { it.kind == UserDevice.KIND_WEBOS && it.host == f.host }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.body)
                            .clickable { if (existing != null) nav.resetTo(Screen.Home, Screen.Remote(existing)) else picked = f }
                            .padding(14.dp),
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(style.accent), contentAlignment = Alignment.Center) {
                            Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(f.name, style = MaterialTheme.typography.titleSmall)
                            Text(if (existing != null) "${f.host} · Đã có trong Thiết bị của tôi" else f.host,
                                style = MaterialTheme.typography.bodySmall, color = if (existing != null) style.accent else palette.inkSoft)
                        }
                    }
                }
                Text("Hoặc nhập địa chỉ IP của TV", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Text("Trên TV: Cài đặt → Mạng → Thông tin mạng.", style = MaterialTheme.typography.bodySmall, color = palette.inkSoft)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manual, onValueChange = { manual = it.filter { c -> c.isDigit() || c == '.' } },
                        placeholder = { Text("192.168.1.20") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { picked = FoundWebOs("TV LG", manual.trim()) },
                        enabled = manual.count { it == '.' } == 3,
                        colors = ButtonDefaults.buttonColors(containerColor = style.accent),
                        modifier = Modifier.padding(start = 10.dp),
                    ) { Text("Kết nối") }
                }
            } else {
                val client = remember(tv.host) { webOsClient(context, tv.host) }
                DisposableEffect(client) {
                    client.acquire()
                    onDispose { client.release() }
                }
                val state by client.state.collectAsState()
                val error by client.lastError.collectAsState()
                LaunchedEffect(state) {
                    if (state == WebOsClient.State.Connected) {
                        val device = withContext(Dispatchers.IO) {
                            val id = deps.devices.addNetwork(tv.name, UserDevice.KIND_WEBOS, tv.host, "LG")
                            deps.devices.all().first { it.id == id }
                        }
                        nav.resetTo(Screen.Home, Screen.Remote(device))
                    }
                }
                when (state) {
                    WebOsClient.State.WaitingForApproval -> {
                        Text("Chấp nhận trên TV", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
                        Text("${tv.name} đang hiện hộp thoại hỏi có cho phép điện thoại này điều khiển không. " +
                            "Dùng remote LG chọn \"Chấp nhận\".", style = MaterialTheme.typography.bodyMedium, color = palette.inkSoft)
                        CircularProgressIndicator(color = style.accent)
                    }
                    WebOsClient.State.Rejected, WebOsClient.State.Unreachable -> {
                        Text(if (state == WebOsClient.State.Rejected) "TV đã từ chối" else "Không kết nối được tới TV",
                            style = MaterialTheme.typography.headlineSmall)
                        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkSoft) }
                        Button(onClick = { scope.launch { client.disconnect(); delay(300); client.connect() } },
                            colors = ButtonDefaults.buttonColors(containerColor = style.accent)) { Text("Thử lại") }
                        TextButton(onClick = { picked = null }) { Text("Chọn TV khác") }
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
                        CircularProgressIndicator(color = style.accent, modifier = Modifier.size(28.dp))
                        Text("Đang kết nối tới ${tv.name}…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

// --- Remote ----------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebOsRemoteScreen(nav: Navigator, device: UserDevice) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val style = deviceStyle("webos")
    val host = device.host ?: return
    val client = remember(host) { webOsClient(context, host) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(client, lifecycle) {
        var held = false
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> if (!held) { held = true; client.acquire() }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> if (held) { held = false; client.release() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (held) client.release()
        }
    }
    val volume by client.volume.collectAsState()
    val apps by client.apps.collectAsState()
    var navMode by remember { mutableStateOf(NavModePref.load(context)) }
    var showInputs by remember { mutableStateOf(false) }

    val sender = remember(client) {
        object : KeySender {
            override fun press(key: KeySpec): Boolean = when (key.funcKey) {
                // Power: off over the socket, on with Wake-on-LAN when the TV is unreachable.
                "POWER" -> if (client.state.value == WebOsClient.State.Connected) { client.powerOff(); true } else client.powerOn()
                else -> client.button((key as WebOsKey).button)
            }
            override fun repeat(key: KeySpec) { client.button((key as WebOsKey).button) }
        }
    }

    RemoteFrame(nav, device.name, "LG webOS · $host", style, above = { WebOsBanner(client) }, headerEnd = { LatencyChip(host) }) {
        CompositionLocalProvider(LocalKeySender provides sender) {
            fun k(name: String) = KEYS.getValue(name)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                PowerKey(k("POWER"))
                Spacer(Modifier.weight(1f))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(start = 10.dp).size(52.dp).clip(CircleShape).background(palette.key)
                        .clickable { showInputs = true },
                ) { Icon(Icons.Filled.Input, "Nguồn vào", tint = palette.ink) }
                IrKey(k("MUTE"), Modifier.padding(start = 10.dp).size(52.dp), shape = CircleShape)
            }
            NavModeSwitch(navMode, style.accent) { navMode = it; NavModePref.save(context, it) }
            if (navMode == NavMode.Keys) {
                DPad(k("UP"), k("DOWN"), k("LEFT"), k("RIGHT"), k("OK"), style.accent, size = 230.dp)
            } else {
                MousePad(client, style.accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                HRocker("Âm lượng", k("VOL_DOWN"), k("VOL_UP"), Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.VolumeUp)
                HRocker("Kênh", k("CH_DOWN"), k("CH_UP"), Modifier.weight(1f))
            }
            volume?.let { v ->
                Text(if (v.muted) "Đang tắt tiếng" else "Âm lượng ${v.level}", style = MaterialTheme.typography.labelMedium, color = palette.inkSoft)
            }
            KeyRow(listOf(k("BACK"), k("HOME"), k("SETTINGS"), k("EXIT")))
            KeyRow(listOf(k("REWIND"), k("PLAY"), k("PAUSE"), k("STOP"), k("FAST_FORWARD")))
            KeyRow(listOf(k("RED"), k("GREEN"), k("YELLOW"), k("BLUE")), height = 20.dp) { com.minhngoc.irremote.ui.remote.KEY_COLORS[it.funcKey] }

            if (apps.isNotEmpty()) {
                Text("Ứng dụng trên TV", style = MaterialTheme.typography.titleSmall, color = palette.inkSoft, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    apps.take(16).forEach { app ->
                        Text(
                            app.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(palette.key)
                                .clickable { client.launch(app.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            TypeToLg(client)
        }
    }

    if (showInputs) InputPicker(client, onDismiss = { showInputs = false })
}

/**
 * Real mouse cursor (webOS pointer socket): drag moves it, tap clicks, the strip on
 * the right scrolls. Moves are batched per frame of travel to keep the socket light.
 */
@Composable
private fun MousePad(client: WebOsClient, accent: Color) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(32.dp))
                .background(palette.groove)
                .pointerInput(client) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        client.move((drag.x * 1.6f).roundToInt(), (drag.y * 1.6f).roundToInt())
                    }
                }
                .pointerInput(client) { detectTapGestures(onTap = { client.click() }) },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Mouse, null, tint = accent, modifier = Modifier.size(36.dp))
                Text("Kéo để di chuột · chạm để click", style = MaterialTheme.typography.bodySmall, color = palette.inkSoft,
                    modifier = Modifier.padding(top = 10.dp))
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(palette.groove)
                .pointerInput(client) {
                    var acc = 0f
                    detectDragGestures(onDragStart = { acc = 0f }) { change, drag ->
                        change.consume()
                        acc += drag.y
                        val step = 24.dp.toPx()
                        while (kotlin.math.abs(acc) >= step) {
                            client.scroll(0, if (acc > 0) 1 else -1)
                            acc -= step * kotlin.math.sign(acc)
                        }
                    }
                },
        ) { Text("Cuộn", style = MaterialTheme.typography.labelSmall, color = palette.inkSoft) }
    }
}

/**
 * Mirrors the phone field into the TV's focused text field. webOS IME only inserts at the
 * cursor and deletes backwards, so each change is sent as "delete what differs, insert the rest".
 */
@Composable
private fun TypeToLg(client: WebOsClient) {
    val palette = LocalPalette.current
    var text by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf("") }
    LaunchedEffect(text) {
        delay(150)
        val common = sent.commonPrefixWith(text).length
        if (sent.length > common) client.deleteCharacters(sent.length - common)
        if (text.length > common) client.insertText(text.substring(common))
        sent = text
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Keyboard, null, tint = palette.inkSoft) },
            placeholder = { Text("Gõ chữ lên TV") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { client.sendEnter() }),
            modifier = Modifier.weight(1f),
        )
        DictateButton { spoken -> text = spoken }
        IconButton(onClick = { client.sendEnter() }, enabled = text.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Send, "Enter trên TV") }
    }
    Text("Mở ô tìm kiếm trên TV trước; chữ gõ hoặc đọc ở đây hiện ngay trên TV.",
        style = MaterialTheme.typography.bodySmall, color = palette.inkSoft, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun InputPicker(client: WebOsClient, onDismiss: () -> Unit) {
    var inputs by remember { mutableStateOf<List<WebOsClient.Input>?>(null) }
    LaunchedEffect(client) { inputs = client.inputs() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn nguồn vào") },
        text = {
            Column {
                val list = inputs
                when {
                    list == null -> CircularProgressIndicator()
                    list.isEmpty() -> Text("TV không trả về danh sách nguồn vào.")
                    else -> list.forEach { input ->
                        Text(input.label, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { client.switchInput(input.id); onDismiss() }.padding(vertical = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}

@Composable
private fun WebOsBanner(client: WebOsClient) {
    val palette = LocalPalette.current
    val state by client.state.collectAsState()
    val error by client.lastError.collectAsState()
    val text = when (state) {
        WebOsClient.State.Connected -> return
        WebOsClient.State.Idle, WebOsClient.State.Connecting -> "Đang kết nối tới TV…"
        WebOsClient.State.WaitingForApproval -> "Chấp nhận yêu cầu kết nối trên TV bằng remote LG."
        WebOsClient.State.Rejected -> "TV đã từ chối kết nối. Xóa thiết bị rồi thêm lại để ghép nối lại."
        WebOsClient.State.Unreachable -> "Chưa kết nối được tới TV. TV đang tắt? Nút nguồn sẽ thử bật bằng Wake-on-LAN."
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(palette.body).padding(14.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = if (state == WebOsClient.State.Connecting || state == WebOsClient.State.Idle) palette.inkSoft else palette.power)
        error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = palette.inkSoft, modifier = Modifier.padding(top = 4.dp)) }
    }
}
