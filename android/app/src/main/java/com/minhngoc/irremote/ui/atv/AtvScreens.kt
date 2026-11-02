package com.minhngoc.irremote.ui.atv

import androidx.compose.ui.res.stringResource
import com.minhngoc.irremote.R
import androidx.annotation.StringRes
import com.minhngoc.irremote.atv.PairingException
import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Keyboard
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhngoc.irremote.atv.AtvDiscovery
import com.minhngoc.irremote.atv.AtvPairing
import com.minhngoc.irremote.atv.AtvRemote
import com.minhngoc.irremote.atv.FoundTv
import com.minhngoc.irremote.data.KeySpec
import com.minhngoc.irremote.net.Latency
import kotlin.math.roundToInt
import com.minhngoc.irremote.data.UserDevice
import com.minhngoc.irremote.ui.AppScaffold
import com.minhngoc.irremote.ui.LocalDeps
import com.minhngoc.irremote.ui.Navigator
import com.minhngoc.irremote.ui.Screen
import com.minhngoc.irremote.ui.loadAsync
import com.minhngoc.irremote.ui.remote.DPad
import com.minhngoc.irremote.ui.remote.KeyRow
import com.minhngoc.irremote.ui.remote.KeySender
import com.minhngoc.irremote.ui.remote.LocalKeySender
import com.minhngoc.irremote.ui.remote.PowerKey
import com.minhngoc.irremote.ui.remote.IrKey
import com.minhngoc.irremote.ui.remote.RemoteFrame
import com.minhngoc.irremote.ui.remote.Rocker
import com.minhngoc.irremote.ui.theme.LocalPalette
import com.minhngoc.irremote.ui.theme.deviceStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A key of the network remote: a normalized function plus the Android key code sent for it. */
data class AtvKey(override val funcKey: String, val keyCode: Int, override val label: String = funcKey) : KeySpec {
    override val id: Long get() = keyCode.toLong()
}

private val KEYS = listOf(
    AtvKey("POWER", KeyEvent.KEYCODE_POWER, "Power"),
    AtvKey("INPUT", KeyEvent.KEYCODE_TV_INPUT, "Input"),
    AtvKey("MUTE", KeyEvent.KEYCODE_VOLUME_MUTE, "Mute"),
    AtvKey("VOL_UP", KeyEvent.KEYCODE_VOLUME_UP), AtvKey("VOL_DOWN", KeyEvent.KEYCODE_VOLUME_DOWN),
    AtvKey("CH_UP", KeyEvent.KEYCODE_CHANNEL_UP), AtvKey("CH_DOWN", KeyEvent.KEYCODE_CHANNEL_DOWN),
    AtvKey("UP", KeyEvent.KEYCODE_DPAD_UP), AtvKey("DOWN", KeyEvent.KEYCODE_DPAD_DOWN),
    AtvKey("LEFT", KeyEvent.KEYCODE_DPAD_LEFT), AtvKey("RIGHT", KeyEvent.KEYCODE_DPAD_RIGHT),
    AtvKey("OK", KeyEvent.KEYCODE_DPAD_CENTER),
    AtvKey("BACK", KeyEvent.KEYCODE_BACK, "Back"), AtvKey("HOME", KeyEvent.KEYCODE_HOME, "Home"),
    AtvKey("SETTINGS", KeyEvent.KEYCODE_SETTINGS, "Settings"),
    AtvKey("REWIND", KeyEvent.KEYCODE_MEDIA_REWIND), AtvKey("PLAY_PAUSE", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
    AtvKey("FAST_FORWARD", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD),
    AtvKey("PREV", KeyEvent.KEYCODE_MEDIA_PREVIOUS), AtvKey("NEXT", KeyEvent.KEYCODE_MEDIA_NEXT),
).associateBy { it.funcKey }

private data class TvApp(val name: String, val link: String, val color: Color)

// Deep links the TV resolves to an installed app; "market://launch" opens a package directly.
private val APPS = listOf(
    TvApp("YouTube", "https://www.youtube.com", Color(0xFFFF0033)),
    TvApp("Netflix", "https://www.netflix.com/title", Color(0xFFE50914)),
    TvApp("Spotify", "https://open.spotify.com", Color(0xFF1DB954)),
    TvApp("Prime Video", "https://app.primevideo.com", Color(0xFF00A8E1)),
    TvApp("YouTube Music", "market://launch?id=com.google.android.youtube.tvmusic", Color(0xFFFF4E45)),
)

// --- Add: find the TV, then pair with the code it shows -----------------------------------

private sealed interface PairStep {
    data object Pick : PairStep
    data class Connecting(val tv: FoundTv) : PairStep
    data class EnterCode(val tv: FoundTv) : PairStep
    data class Failed(val tv: FoundTv, val message: String) : PairStep
}

@Composable
fun AtvAddScreen(nav: Navigator) {
    val deps = LocalDeps.current
    val palette = LocalPalette.current
    val context = LocalContext.current
    val style = deviceStyle("atv")
    var step by remember { mutableStateOf<PairStep>(PairStep.Pick) }
    fun explain(e: Exception, @StringRes fallback: Int, vararg args: Any): String =
        (e as? PairingException)?.let { context.getString(it.res, *it.args) } ?: context.getString(fallback, *args)
    var pairing by remember { mutableStateOf<AtvPairing?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { pairing?.close() } }

    fun begin(tv: FoundTv) {
        step = PairStep.Connecting(tv)
        scope.launch {
            step = try {
                withContext(Dispatchers.IO) {
                    pairing?.close()
                    val p = AtvPairing(tv.host, deps.atvIdentity.value)
                    pairing = p
                    p.start("${Build.MANUFACTURER} ${Build.MODEL}")
                }
                PairStep.EnterCode(tv)
            } catch (e: Exception) {
                PairStep.Failed(tv, explain(e, R.string.cannot_reach_host, tv.host))
            }
        }
    }

    AppScaffold(stringResource(R.string.atv_tile_title), nav, subtitle = stringResource(R.string.atv_add_sub), style = style) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = step) {
                PairStep.Pick -> PickTv(onPick = ::begin)
                is PairStep.Connecting -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
                    CircularProgressIndicator(color = style.accent, modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.connecting_to, s.tv.name), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                }
                is PairStep.Failed -> {
                    Text(stringResource(R.string.pair_failed_title), style = MaterialTheme.typography.headlineSmall)
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = palette.inkSoft)
                    Text(stringResource(R.string.check_same_wifi), style = MaterialTheme.typography.bodySmall, color = palette.inkSoft)
                    Button(onClick = { begin(s.tv) }, colors = ButtonDefaults.buttonColors(containerColor = style.accent)) { Text(stringResource(R.string.retry)) }
                    TextButton(onClick = { step = PairStep.Pick }) { Text(stringResource(R.string.pick_other_tv)) }
                }
                is PairStep.EnterCode -> EnterCode(s.tv) { code ->
                    val p = pairing ?: return@EnterCode context.getString(R.string.connection_lost_retry)
                    try {
                        withContext(Dispatchers.IO) { p.finish(code) }
                        val device = withContext(Dispatchers.IO) {
                            val id = deps.devices.addNetwork(s.tv.name, UserDevice.KIND_ATV, s.tv.host, "Android TV")
                            deps.devices.all().first { it.id == id }
                        }
                        pairing?.close()
                        nav.resetTo(Screen.Home, Screen.Remote(device))
                        null
                    } catch (e: Exception) {
                        explain(e, R.string.pair_failed_title)
                    }
                }
            }
        }
    }
}

@Composable
private fun PickTv(onPick: (FoundTv) -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val style = deviceStyle("atv")
    val found by remember { AtvDiscovery.scan(context) }.collectAsState(initial = emptyList())
    var manual by remember { mutableStateOf("") }

    Text(stringResource(R.string.tvs_on_network), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    if (found.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = style.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.searching_atv), style = MaterialTheme.typography.bodySmall,
                color = palette.inkSoft, modifier = Modifier.padding(start = 12.dp))
        }
    }
    val deps = LocalDeps.current
    val saved by loadAsync(Unit) { deps.devices.all() }
    found.forEach { tv ->
        val existing = saved?.any { it.kind == UserDevice.KIND_ATV && it.host == tv.host } == true
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.body)
                .clickable { onPick(tv) }.padding(14.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(style.accent), contentAlignment = Alignment.Center) {
                Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(tv.name, style = MaterialTheme.typography.titleSmall)
                // Picking a saved box re-pairs it; the existing entry is kept, not duplicated.
                Text(if (existing) stringResource(R.string.already_saved_repair, tv.host) else tv.host,
                    style = MaterialTheme.typography.bodySmall, color = if (existing) style.accent else palette.inkSoft)
            }
        }
    }

    Text(stringResource(R.string.or_enter_ip), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
    Text(stringResource(R.string.atv_ip_hint), style = MaterialTheme.typography.bodySmall, color = palette.inkSoft)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = manual, onValueChange = { manual = it.filter { c -> c.isDigit() || c == '.' } },
            placeholder = { Text("192.168.1.20") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onPick(FoundTv("Android TV", manual.trim(), 6466)) },
            enabled = manual.count { it == '.' } == 3,
            colors = ButtonDefaults.buttonColors(containerColor = style.accent),
            modifier = Modifier.padding(start = 10.dp),
        ) { Text(stringResource(R.string.connect)) }
    }
}

@Composable
private fun EnterCode(tv: FoundTv, submit: suspend (String) -> String?) {
    val palette = LocalPalette.current
    val style = deviceStyle("atv")
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun go() {
        busy = true
        scope.launch { error = submit(code); busy = false }
    }

    Text(stringResource(R.string.enter_tv_code), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
    Text(stringResource(R.string.tv_shows_code, tv.name), style = MaterialTheme.typography.bodyMedium, color = palette.inkSoft)
    OutlinedTextField(
        value = code,
        onValueChange = { v -> code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6); error = null },
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 8.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (code.length == 6) go() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = ::go,
        enabled = code.length == 6 && !busy,
        colors = ButtonDefaults.buttonColors(containerColor = style.accent),
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(if (busy) R.string.pairing_progress else R.string.pair)) }
}

// --- Remote ------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AtvRemoteScreen(nav: Navigator, device: UserDevice) {
    val deps = LocalDeps.current
    val palette = LocalPalette.current
    val style = deviceStyle("atv")
    val host = device.host ?: return
    val identity by loadAsync(Unit) { deps.atvIdentity.value }
    val client = identity?.let { remember(host) { AtvRemote.of(host, it) } }

    RemoteFrame(nav, device.name, "Android TV · $host", style, loading = client == null, above = {
        client?.let { ConnectionBanner(it, onRepair = { nav.push(Screen.AtvAdd) }) }
    }, headerEnd = { LatencyChip(host) }) {
        client ?: return@RemoteFrame
        // Hold the connection only while this screen is visible: a backgrounded app must not
        // keep (or re-open) a session, the TV then routes keyboard events to the stale one.
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
        val context = LocalContext.current
        var navMode by remember { mutableStateOf(NavModePref.load(context)) }
        val sender = remember(client) {
            object : KeySender {
                override fun press(key: KeySpec): Boolean = client.sendKey((key as AtvKey).keyCode)
                override fun repeat(key: KeySpec) { client.sendKey((key as AtvKey).keyCode) }
            }
        }
        CompositionLocalProvider(LocalKeySender provides sender) {
            fun k(name: String) = KEYS.getValue(name)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                PowerKey(k("POWER"))
                Spacer(Modifier.weight(1f))
                listOf(k("INPUT"), k("MUTE")).forEach { IrKey(it, Modifier.padding(start = 10.dp).size(52.dp), shape = CircleShape) }
            }
            // Navigation on its own row, away from volume/channel, so thumbs don't hit the wrong key.
            NavModeSwitch(navMode, style.accent) { navMode = it; NavModePref.save(context, it) }
            if (navMode == NavMode.Keys) {
                DPad(k("UP"), k("DOWN"), k("LEFT"), k("RIGHT"), k("OK"), style.accent, size = 230.dp)
            } else {
                Touchpad(client, style.accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                HRocker(stringResource(R.string.volume), k("VOL_DOWN"), k("VOL_UP"), Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.VolumeUp)
                HRocker(stringResource(R.string.channel_short), k("CH_DOWN"), k("CH_UP"), Modifier.weight(1f))
            }
            // Boxes that pass audio over HDMI report 0/0: nothing useful to show.
            volume?.takeIf { it.max > 0 }?.let { v ->
                Text(
                    if (v.muted) stringResource(R.string.muted) else stringResource(R.string.volume_level_of, v.level, v.max),
                    style = MaterialTheme.typography.labelMedium, color = palette.inkSoft,
                )
            }
            HoldToTalk(client, style.accent)
            KeyRow(listOf(k("BACK"), k("HOME"), k("SETTINGS")))
            KeyRow(listOf(k("PREV"), k("REWIND"), k("PLAY_PAUSE"), k("FAST_FORWARD"), k("NEXT")))

            Text(stringResource(R.string.open_app), style = MaterialTheme.typography.titleSmall, color = palette.inkSoft, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                APPS.forEach { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(palette.key)
                            .clickable { client.launchApp(app.link) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(app.color))
                        Text(app.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            TypeToTv(client)
        }
    }
}

@Composable
private fun TypeToTv(client: AtvRemote) {
    val palette = LocalPalette.current
    var text by remember { mutableStateOf("") }
    var edited by remember { mutableStateOf(false) }
    // The TV field mirrors this one once the user types: every change replaces the TV text.
    LaunchedEffect(text) {
        if (!edited) return@LaunchedEffect // don't wipe what is already on the TV
        kotlinx.coroutines.delay(150)
        client.sendText(text)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text, onValueChange = { text = it; edited = true }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Keyboard, null, tint = palette.inkSoft) },
            placeholder = { Text(stringResource(R.string.type_on_tv)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { client.sendKey(KeyEvent.KEYCODE_ENTER) }),
            modifier = Modifier.weight(1f),
        )
        DictateButton { spoken -> text = spoken; edited = true }
        IconButton(onClick = { client.sendKey(KeyEvent.KEYCODE_ENTER) }, enabled = text.isNotEmpty()) {
            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.enter_on_tv))
        }
    }
    val fieldOpen by client.textFieldOpen.collectAsState()
    Text(
        stringResource(if (fieldOpen) R.string.tv_field_open else R.string.tv_field_closed),
        style = MaterialTheme.typography.bodySmall,
        color = if (fieldOpen) Color(0xFF30A46C) else palette.inkSoft,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConnectionBanner(client: AtvRemote, onRepair: () -> Unit) {
    val palette = LocalPalette.current
    val state by client.state.collectAsState()
    val (text, color) = when (state) {
        AtvRemote.State.Connected -> return
        AtvRemote.State.Idle, AtvRemote.State.Connecting -> stringResource(R.string.connecting_tv) to palette.inkSoft
        AtvRemote.State.Unreachable -> stringResource(R.string.atv_unreachable) to palette.power
        AtvRemote.State.NotPaired -> stringResource(R.string.atv_not_paired) to palette.power
    }
    val error by client.lastError.collectAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(palette.body).padding(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = color)
            error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = palette.inkSoft, modifier = Modifier.padding(top = 4.dp)) }
        }
        if (state == AtvRemote.State.NotPaired) TextButton(onClick = onRepair) { Text(stringResource(R.string.pair_again)) }
    }
}

/** Live round-trip time to a network device, refreshed every few seconds. */
@Composable
fun LatencyChip(host: String) {
    val palette = LocalPalette.current
    val rtt by remember(host) { Latency.monitor(host) }.collectAsState(initial = Float.NaN)
    val (label, dot) = when {
        rtt?.isNaN() == true -> "…" to palette.inkSoft
        rtt == null -> stringResource(R.string.latency_offline) to palette.power
        rtt!! < 50f -> "${rtt!!.roundToInt()} ms" to Color(0xFF30A46C)
        rtt!! < 150f -> "${rtt!!.roundToInt()} ms" to Color(0xFFE8A800)
        else -> "${rtt!!.roundToInt()} ms" to palette.power
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(CircleShape).background(palette.body).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(label, style = MaterialTheme.typography.labelMedium, color = palette.inkSoft, modifier = Modifier.padding(start = 6.dp))
    }
}
