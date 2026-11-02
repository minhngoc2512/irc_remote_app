package com.minhngoc.irremote.webos

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Where a TV's pairing key and MAC address are remembered (SharedPreferences in the app). */
interface WebOsStore {
    fun clientKey(host: String): String?
    fun saveClientKey(host: String, key: String)
    fun mac(host: String): String?
    fun saveMac(host: String, mac: String)
}

/**
 * LG webOS remote over SSAP: JSON requests on a WebSocket (ws://:3000, or wss://:3001
 * when the firmware enforces TLS), plus a second "pointer input" socket for keys and
 * the mouse cursor. Pairing: the TV shows a prompt; once accepted it returns a
 * client-key that skips the prompt next time.
 */
class WebOsClient(val host: String, private val store: WebOsStore) {

    enum class State { Idle, Connecting, WaitingForApproval, Connected, Rejected, Unreachable }

    data class Volume(val level: Int, val muted: Boolean)
    data class App(val id: String, val title: String)
    data class Input(val id: String, val label: String)

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError
    private val _volume = MutableStateFlow<Volume?>(null)
    val volume: StateFlow<Volume?> = _volume
    private val _apps = MutableStateFlow<List<App>>(emptyList())
    val apps: StateFlow<List<App>> = _apps
    private val _foregroundApp = MutableStateFlow<String?>(null)
    val foregroundApp: StateFlow<String?> = _foregroundApp
    private val _modelName = MutableStateFlow<String?>(null)
    val modelName: StateFlow<String?> = _modelName

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .sslSocketFactory(trustAll.socketFactory, TrustAny) // the TV's certificate is self-signed
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(4, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    @Volatile private var main: WebSocket? = null
    @Volatile private var pointer: WebSocket? = null
    private val ids = AtomicInteger()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private var connectJob: Job? = null

    // --- Lifetime: same model as the Android TV client -------------------------------------

    @Volatile private var holders = 0
    private var idleClose: Job? = null

    @Synchronized
    fun acquire() {
        holders++
        idleClose?.cancel()
        connect()
    }

    @Synchronized
    fun release() {
        holders = maxOf(0, holders - 1)
        scheduleIdleClose()
    }

    @Synchronized
    private fun scheduleIdleClose() {
        if (holders > 0) return
        idleClose?.cancel()
        idleClose = scope.launch {
            delay(IDLE_CLOSE_MS)
            synchronized(this@WebOsClient) { if (holders == 0) disconnect() }
        }
    }

    /** Opens the session (and pairs if needed). Safe to call repeatedly. */
    fun connect() {
        if (connectJob?.isActive == true || _state.value == State.Connected) return
        connectJob = scope.launch {
            _state.value = State.Connecting
            _lastError.value = null
            for (url in listOf("ws://$host:3000", "wss://$host:3001")) {
                val opened = openMain(url)
                if (opened) return@launch
            }
            _state.value = State.Unreachable
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        pointer?.close(1000, null)
        main?.close(1000, null)
        pointer = null
        main = null
        pending.values.forEach { it.cancel() }
        pending.clear()
        if (_state.value != State.Rejected) _state.value = State.Idle
    }

    /** Connects one URL; true once the socket opened (pairing then continues in callbacks). */
    private suspend fun openMain(url: String): Boolean {
        val opened = CompletableDeferred<Boolean>()
        val ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(true)
                main = webSocket
                // Newer webOS wants system info requested before registering.
                send(webSocket, JSONObject().put("id", "sysinfo").put("type", "request")
                    .put("uri", "ssap://system/getSystemInfo").put("payload", JSONObject()))
                send(webSocket, registration())
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(webSocket, JSONObject(text))

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "$url failed: ${t.message}")
                if (!opened.isCompleted) {
                    opened.complete(false)
                    return
                }
                if (main === webSocket) lost("${t.javaClass.simpleName}: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (main === webSocket) lost("Closed $code $reason")
            }
        })
        val ok = withTimeoutOrNull(6_000) { opened.await() } ?: false
        if (!ok) ws.cancel()
        return ok
    }

    private fun lost(why: String) {
        _lastError.value = why
        main = null
        pointer = null
        if (_state.value == State.Connected || _state.value == State.Connecting) _state.value = State.Idle
        if (holders > 0) scope.launch { delay(2_000); connect() } // a visible remote reconnects
    }

    private fun handle(ws: WebSocket, msg: JSONObject) {
        val type = msg.optString("type")
        val id = msg.optString("id")
        when {
            id == "register_0" && type == "response" ->
                if (msg.optJSONObject("payload")?.optString("pairingType") == "PROMPT") _state.value = State.WaitingForApproval
            id == "register_0" && type == "registered" -> {
                msg.optJSONObject("payload")?.optString("client-key")?.takeIf { it.isNotEmpty() }?.let { store.saveClientKey(host, it) }
                _state.value = State.Connected
                scope.launch { afterRegistered() }
            }
            id == "register_0" && type == "error" -> {
                _lastError.value = msg.optString("error")
                _state.value = State.Rejected
                ws.close(1000, null)
            }
            id == "sysinfo" -> _modelName.value = msg.optJSONObject("payload")?.optString("modelName")?.ifEmpty { null }
            id == "volume" -> parseVolume(msg.optJSONObject("payload"))
            id == "foreground" -> _foregroundApp.value = msg.optJSONObject("payload")?.optString("appId")?.ifEmpty { null }
            else -> pending.remove(id)?.complete(msg)
        }
    }

    private suspend fun afterRegistered() {
        // Key/mouse events go over a separate socket whose address the TV hands out.
        request("com.webos.service.networkinput/getPointerInputSocket")?.optString("socketPath")?.takeIf { it.isNotEmpty() }?.let { path ->
            pointer = http.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (pointer === webSocket) pointer = null
                }
            })
        }
        subscribe("volume", "audio/getVolume")
        subscribe("foreground", "com.webos.applicationManager/getForegroundAppInfo")
        request("com.webos.applicationManager/listLaunchPoints")?.optJSONArray("launchPoints")?.let { arr ->
            _apps.value = (0 until arr.length()).map { arr.getJSONObject(it) }
                .map { App(it.optString("id"), it.optString("title")) }
                .filter { it.id.isNotEmpty() && it.title.isNotEmpty() }
        }
        // Remember the MAC so the TV can be switched on later with Wake-on-LAN.
        request("com.webos.service.connectionmanager/getinfo")?.let { info ->
            val mac = info.optJSONObject("wiredInfo")?.optString("macAddress")?.takeIf { it.isNotEmpty() && info.optJSONObject("wiredInfo")?.optString("state") == "connected" }
                ?: info.optJSONObject("wifiInfo")?.optString("macAddress")?.takeIf { it.isNotEmpty() }
                ?: info.optJSONObject("wiredInfo")?.optString("macAddress")?.takeIf { it.isNotEmpty() }
            mac?.let { store.saveMac(host, it) }
        }
    }

    private fun parseVolume(p: JSONObject?) {
        p ?: return
        val status = p.optJSONObject("volumeStatus") // webOS 5+
        val level = status?.optInt("volume", -1) ?: p.optInt("volume", -1)
        val muted = status?.optBoolean("muteStatus") ?: p.optBoolean("muted")
        if (level >= 0) _volume.value = Volume(level, muted)
    }

    private fun registration(): JSONObject {
        val payload = JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .put("manifest", JSONObject()
                .put("appVersion", "1.1")
                .put("manifestVersion", 1)
                .put("permissions", JSONArray(PERMISSIONS)))
        store.clientKey(host)?.let { payload.put("client-key", it) }
        return JSONObject().put("type", "register").put("id", "register_0").put("payload", payload)
    }

    // --- Requests ------------------------------------------------------------------------------

    /** Sends an SSAP request and waits for its response payload (null on failure/timeout). */
    suspend fun request(uri: String, payload: JSONObject = JSONObject()): JSONObject? {
        val ws = main ?: return null
        val id = "req_${ids.incrementAndGet()}"
        val result = CompletableDeferred<JSONObject>()
        pending[id] = result
        send(ws, JSONObject().put("id", id).put("type", "request").put("uri", "ssap://$uri").put("payload", payload))
        val msg = withTimeoutOrNull(5_000) { result.await() }
        pending.remove(id)
        if (msg == null || msg.optString("type") == "error") {
            Log.w(TAG, "$uri failed: ${msg?.optString("error")}")
            return null
        }
        return msg.optJSONObject("payload") ?: JSONObject()
    }

    private fun subscribe(id: String, uri: String) {
        main?.let { send(it, JSONObject().put("id", id).put("type", "subscribe").put("uri", "ssap://$uri").put("payload", JSONObject())) }
    }

    /** Fire-and-forget request, for buttons. */
    fun call(uri: String, payload: JSONObject = JSONObject()) {
        if (_state.value != State.Connected) connect()
        scheduleIdleClose()
        scope.launch { waitConnected(); request(uri, payload) }
    }

    private suspend fun waitConnected() {
        withTimeoutOrNull(4_000) { while (_state.value != State.Connected) delay(50) }
    }

    // --- Pointer socket: remote keys and the mouse cursor ------------------------------------

    /** Presses a remote key by its webOS name (UP, ENTER, BACK, HOME, VOLUMEUP, 1, RED, ...). */
    fun button(name: String) = pointerSend("type:button\nname:$name\n\n")

    fun move(dx: Int, dy: Int) = pointerSend("type:move\ndx:$dx\ndy:$dy\ndown:0\n\n", queueIfOffline = false)

    fun click() = pointerSend("type:click\n\n")

    fun scroll(dx: Int, dy: Int) = pointerSend("type:scroll\ndx:$dx\ndy:$dy\n\n", queueIfOffline = false)

    private fun pointerSend(frame: String, queueIfOffline: Boolean = true): Boolean {
        pointer?.let { return it.send(frame) }
        if (!queueIfOffline) return false
        if (_state.value != State.Connected) connect()
        scheduleIdleClose()
        scope.launch {
            withTimeoutOrNull(5_000) { while (pointer == null) delay(50) }
            pointer?.send(frame)
        }
        return true
    }

    // --- Higher-level commands --------------------------------------------------------------

    fun powerOff() = call("system/turnOff")

    /** Wake-on-LAN with the MAC learned while paired; the TV must allow "Turn on via Wi-Fi". */
    fun powerOn(): Boolean {
        val mac = store.mac(host) ?: return false
        scope.launch { WakeOnLan.send(mac, host) }
        return true
    }

    fun launch(appId: String) = call("system.launcher/launch", JSONObject().put("id", appId))

    fun setMute(mute: Boolean) = call("audio/setMute", JSONObject().put("mute", mute))

    suspend fun inputs(): List<Input> =
        request("tv/getExternalInputList")?.optJSONArray("devices")?.let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it) }.map { Input(it.optString("id"), it.optString("label")) }
        }.orEmpty()

    fun switchInput(id: String) = call("tv/switchInput", JSONObject().put("inputId", id))

    /** Text into the TV's focused field (its on-screen keyboard must be open). */
    fun insertText(text: String) = call("com.webos.service.ime/insertText", JSONObject().put("text", text).put("replace", 0))

    fun deleteCharacters(count: Int) = call("com.webos.service.ime/deleteCharacters", JSONObject().put("count", count))

    fun sendEnter() = call("com.webos.service.ime/sendEnterKey")

    fun toast(message: String) = call("system.notifications/createToast", JSONObject().put("message", message))

    private fun send(ws: WebSocket, json: JSONObject) {
        ws.send(json.toString())
    }

    companion object {
        private const val TAG = "WebOsClient"
        private const val IDLE_CLOSE_MS = 15_000L

        // Same permission set as aiowebostv (home-assistant-libs), which LG TVs accept unsigned.
        private val PERMISSIONS = listOf(
            "APP_TO_APP", "CLOSE", "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK",
            "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_MEDIA_RECORDING", "CONTROL_INPUT_TEXT",
            "CONTROL_INPUT_TV", "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_POWER", "CONTROL_TV_SCREEN", "LAUNCH",
            "LAUNCH_WEBAPP", "READ_APP_STATUS", "READ_COUNTRY_INFO", "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST",
            "READ_INSTALLED_APPS", "READ_LGE_SDX", "READ_LGE_TV_INPUT_EVENTS", "READ_NETWORK_STATE",
            "READ_NOTIFICATIONS", "READ_POWER_STATE", "READ_RUNNING_APPS", "READ_SETTINGS", "READ_TV_CHANNEL_LIST",
            "READ_TV_CURRENT_TIME", "READ_UPDATE_INFO", "SEARCH", "TEST_OPEN", "TEST_PROTECTED", "TEST_SECURE",
            "UPDATE_FROM_REMOTE_APP", "WRITE_NOTIFICATION_ALERT", "WRITE_NOTIFICATION_TOAST", "WRITE_SETTINGS",
        )

        private object TrustAny : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        private val trustAll: SSLContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(TrustAny), SecureRandom()) }

        private val clients = ConcurrentHashMap<String, WebOsClient>()

        fun of(host: String, store: WebOsStore): WebOsClient = clients.getOrPut(host) { WebOsClient(host, store) }
    }
}
