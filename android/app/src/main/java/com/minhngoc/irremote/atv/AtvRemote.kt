package com.minhngoc.irremote.atv

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket

/**
 * Control connection on port 6466 (remotemessage.proto). The TV opens with
 * remote_configure, we answer with our features, then it pings every few
 * seconds; keys are RemoteKeyInject with Android KeyEvent codes.
 */
class AtvRemote(val host: String, private val identity: AtvIdentity, private val port: Int = 6466) {

    enum class State { Idle, Connecting, Connected, NotPaired, Unreachable }

    data class Volume(val level: Int, val max: Int, val muted: Boolean)

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state
    private val _volume = MutableStateFlow<Volume?>(null)
    val volume: StateFlow<Volume?> = _volume
    /** Last connection error, shown under the status so failures can be diagnosed on the phone. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError
    /** Package of the app in the foreground on the TV, when it reports one. */
    private val _currentApp = MutableStateFlow<String?>(null)
    val currentApp: StateFlow<String?> = _currentApp

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "atv-write-$host") }
    private var readJob: Job? = null
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var out: OutputStream? = null
    @Volatile private var tcpOpen = false
    @Volatile private var talked = false

    // --- Lifetime: connected only while a screen holds the client, or briefly after a one-off send.
    @Volatile private var holders = 0
    private var idleClose: Job? = null

    /** A visible remote screen needs the connection. Pair with [release]. */
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

    /** One-off senders (home screen power key) keep the link only for a short while. */
    @Synchronized
    private fun scheduleIdleClose() {
        if (holders > 0) return
        idleClose?.cancel()
        idleClose = scope.launch {
            delay(IDLE_CLOSE_MS)
            synchronized(this@AtvRemote) { if (holders == 0) disconnect() }
        }
    }

    /** True while the TV reports a focused text field to this connection (IME messages). */
    private val _textFieldOpen = MutableStateFlow(false)
    val textFieldOpen: StateFlow<Boolean> = _textFieldOpen

    /** Opens the connection if needed; safe to call repeatedly. */
    fun connect() {
        if (readJob?.isActive == true) return
        readJob = scope.launch {
            var attempt = 0
            var rejected = 0
            while (isActive) {
                _state.value = State.Connecting
                talked = false
                try {
                    session()
                    attempt = 0
                } catch (e: Exception) {
                    Log.e(TAG, "connection to $host lost (talked=$talked tcp=$tcpOpen)", e)
                    _lastError.value = "${e.javaClass.simpleName}: ${e.message} (tcp=$tcpOpen, talked=$talked)"
                    // A TV that has not paired with our certificate fails the TLS handshake or
                    // hangs up before saying anything; an unreachable one never accepts TCP.
                    if (e is SSLHandshakeException || (tcpOpen && !talked)) rejected++
                    if (rejected >= 2) {
                        _state.value = State.NotPaired
                        closeSocket()
                        return@launch
                    }
                    if (talked) attempt = 0
                    _state.value = if (attempt >= 2) State.Unreachable else State.Connecting
                }
                closeSocket()
                attempt++
                delay(if (attempt < 3) 1_000L else 5_000L)
            }
        }
    }

    fun disconnect() {
        _textFieldOpen.value = false
        readJob?.cancel()
        readJob = null
        closeSocket()
        _state.value = State.Idle
    }

    fun sendKey(keyCode: Int, direction: Direction = Direction.SHORT) =
        write(ProtoWriter().message(10) { varint(1, keyCode.toLong()); varint(2, direction.wire) })

    /** Opens an app by deep link, e.g. "https://www.youtube.com" or "market://launch?id=<package>". */
    fun launchApp(link: String) = write(ProtoWriter().message(90) { string(1, link) })

    /**
     * Sets the content of the text field focused on the TV (an on-screen keyboard must be
     * open there). Each call replaces the whole field. Mi Box only accepts the zeroed
     * counters / insert=0 / position 11 form documented with working byte dumps; the
     * counters the TV reports in remote_ime_batch_edit get the edit ignored.
     */
    fun sendText(text: String) {
        trace("-> text '$text' (state=${_state.value})")
        write(ProtoWriter().message(21) {
            varint(1, 0)
            varint(2, 0)
            message(3) {
                varint(1, 0)
                message(2) { varint(1, 11); varint(2, 11); string(3, text) }
            }
        })
    }

    enum class Direction(val wire: Long) { START_LONG(1), END_LONG(2), SHORT(3) }

    // --- Voice: the TV's assistant listens to audio streamed from the phone -------------------

    @Volatile private var voiceBegin: kotlinx.coroutines.CompletableDeferred<Long>? = null

    /**
     * Opens a voice session: presses SEARCH, waits for the TV's remote_voice_begin and
     * echoes it back. Returns the session id for [sendVoice] / [endVoice], or null if
     * the TV did not open one (no assistant, or voice not supported).
     */
    suspend fun startVoice(): Long? {
        val begin = kotlinx.coroutines.CompletableDeferred<Long>()
        voiceBegin = begin
        sendKey(KEYCODE_SEARCH)
        trace("-> voice: SEARCH (state=${_state.value})")
        val id = kotlinx.coroutines.withTimeoutOrNull(4_000) { begin.await() }
        trace("voice session=$id")
        voiceBegin = null
        if (id != null) write(ProtoWriter().message(30) { varint(1, id) })
        return id
    }

    /** Streams PCM 16-bit mono 8 kHz audio; pieces are split/padded to the sizes the TV accepts. */
    fun sendVoice(sessionId: Long, pcm: ByteArray) {
        trace("-> voice ${pcm.size} bytes")
        var i = 0
        while (i < pcm.size) {
            var piece = pcm.copyOfRange(i, minOf(i + VOICE_CHUNK_MAX, pcm.size))
            if (piece.size < VOICE_CHUNK_MIN) piece = piece.copyOf(VOICE_CHUNK_MIN) // zero padding
            write(ProtoWriter().message(31) { varint(1, sessionId); bytes(2, piece) })
            i += VOICE_CHUNK_MAX
        }
    }

    fun endVoice(sessionId: Long): Boolean {
        trace("-> voice end $sessionId")
        return write(ProtoWriter().message(32) { varint(1, sessionId) })
    }

    private fun write(message: ProtoWriter): Boolean {
        val body = message.toByteArray()
        if (_state.value != State.Connected) connect()
        scheduleIdleClose()
        writer.execute {
            // Give a fresh connection a moment to finish its handshake before dropping the key.
            var waited = 0
            while (_state.value != State.Connected && waited < 3_000) {
                Thread.sleep(50)
                waited += 50
            }
            runCatching { synchronized(this@AtvRemote) { out?.writeFrame(body) } }
                .onFailure { Log.w(TAG, "write to $host failed", it) }
        }
        return true
    }

    private fun session() {
        tcpOpen = false
        // Not inside apply {}: there `port` would silently resolve to SSLSocket.port (0 before connecting).
        val s = identity.sslContext.socketFactory.createSocket() as SSLSocket
        socket = s
        s.connect(InetSocketAddress(host, port), 4_000)
        tcpOpen = true
        s.soTimeout = 20_000 // the TV pings about every 5 s
        s.startHandshake()
        out = s.outputStream
        val input = s.inputStream
        while (true) {
            val raw = input.readFrame()
            val msg = ProtoMessage.parse(raw)
            if (!msg.has(8)) trace("<- fields ${(1..100).filter { msg.has(it) }} ${raw.size}B")
            talked = true
            _lastError.value = null
            when {
                msg.has(1) -> { // remote_configure: answer with the features both sides support
                    val supported = msg.message(1)?.long(1) ?: FEATURES
                    reply(ProtoWriter().message(1) {
                        varint(1, FEATURES and supported)
                        message(2) {
                            string(1, android.os.Build.MODEL ?: "Android"); string(2, android.os.Build.MANUFACTURER ?: "Android")
                            varint(3, 1); string(4, "1"); string(5, "atvremote"); string(6, "1.0.0")
                        }
                    })
                }
                msg.has(2) -> { // remote_set_active
                    reply(ProtoWriter().message(2) { varint(1, FEATURES) })
                    _state.value = State.Connected
                }
                msg.has(8) -> reply(ProtoWriter().message(9) { varint(1, msg.message(8)?.long(1) ?: 0) }) // ping
                msg.has(50) -> msg.message(50)?.let { v -> _volume.value = Volume(v.int(7), v.int(6), v.bool(8)) }
                msg.has(20) -> msg.message(20)?.let {
                    _currentApp.value = it.message(1)?.string(12)
                    _textFieldOpen.value = it.has(2)
                }
                msg.has(40) -> _state.value = State.Connected
                msg.has(30) -> {
                    trace("<- voice_begin ${msg.message(30)?.long(1)}")
                    voiceBegin?.complete(msg.message(30)?.long(1) ?: 0)
                }
                msg.has(22) -> msg.message(22)?.message(2)?.let {
                    _textFieldOpen.value = true
                    trace("<- TV field now '${it.string(2)}'")
                }
                msg.has(21) -> {
                    _textFieldOpen.value = true
                    trace("<- ime_batch_edit ${msg.message(21)?.long(1)}/${msg.message(21)?.long(2)}")
                }
                msg.has(3) -> Log.w(TAG, "TV reported an error")
            }
        }
    }

    /** Protocol trace at error level: MIUI drops info/warn logs from apps. */
    private fun trace(line: String) = Log.e("AtvTrace", "$host $line")

    private fun reply(message: ProtoWriter) {
        val body = message.toByteArray()
        synchronized(this) { out?.writeFrame(body) }
    }

    private fun closeSocket() {
        runCatching { socket?.close() }
        socket = null
        out = null
    }

    companion object {
        private const val TAG = "AtvRemote"
        private const val KEYCODE_SEARCH = 84
        private const val IDLE_CLOSE_MS = 15_000L
        // PING | KEY | IME | VOICE | POWER | VOLUME | APP_LINK
        private const val FEATURES = 1L or 2L or 4L or 8L or 32L or 64L or 512L
        private const val VOICE_CHUNK_MIN = 8 * 1024
        private const val VOICE_CHUNK_MAX = 20 * 1024

        private val clients = ConcurrentHashMap<String, AtvRemote>()

        /** One shared connection per TV, so the home screen and the remote reuse it. */
        fun of(host: String, identity: AtvIdentity): AtvRemote = clients.getOrPut(host) { AtvRemote(host, identity) }
    }
}
