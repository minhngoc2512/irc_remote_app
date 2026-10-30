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
    @Volatile private var imeCounter = 0
    @Volatile private var fieldCounter = 0

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
                    Log.w(TAG, "connection to $host lost: ${e.message}")
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
        readJob?.cancel()
        readJob = null
        closeSocket()
        _state.value = State.Idle
    }

    fun sendKey(keyCode: Int, direction: Direction = Direction.SHORT) =
        write(ProtoWriter().message(10) { varint(1, keyCode.toLong()); varint(2, direction.wire) })

    /** Opens an app by deep link, e.g. "https://www.youtube.com" or "market://launch?id=<package>". */
    fun launchApp(link: String) = write(ProtoWriter().message(90) { string(1, link) })

    /** Types into the text field focused on the TV (needs an on-screen keyboard to be open there). */
    fun sendText(text: String) {
        if (text.isEmpty()) return
        val pos = (text.length - 1).toLong()
        write(ProtoWriter().message(21) {
            varint(1, imeCounter.toLong())
            varint(2, fieldCounter.toLong())
            message(3) {
                varint(1, 1)
                message(2) { varint(1, pos); varint(2, pos); string(3, text) }
            }
        })
    }

    enum class Direction(val wire: Long) { START_LONG(1), END_LONG(2), SHORT(3) }

    private fun write(message: ProtoWriter): Boolean {
        val body = message.toByteArray()
        if (_state.value != State.Connected) connect()
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
        val s = (identity.sslContext.socketFactory.createSocket() as SSLSocket).apply {
            socket = this
            connect(InetSocketAddress(host, port), 4_000)
            tcpOpen = true
            soTimeout = 20_000 // the TV pings about every 5 s
            startHandshake()
        }
        out = s.outputStream
        val input = s.inputStream
        while (true) {
            val msg = ProtoMessage.parse(input.readFrame())
            talked = true
            when {
                msg.has(1) -> { // remote_configure: answer with the features both sides support
                    val supported = msg.message(1)?.long(1) ?: FEATURES
                    reply(ProtoWriter().message(1) {
                        varint(1, FEATURES and supported)
                        message(2) {
                            string(1, android.os.Build.MODEL); string(2, android.os.Build.MANUFACTURER)
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
                msg.has(20) -> _currentApp.value = msg.message(20)?.message(1)?.string(12)
                msg.has(21) -> msg.message(21)?.let { imeCounter = it.int(1); fieldCounter = it.int(2) }
                msg.has(40) -> _state.value = State.Connected
                msg.has(3) -> Log.w(TAG, "TV reported an error")
            }
        }
    }

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
        // PING | KEY | IME | POWER | VOLUME | APP_LINK
        private const val FEATURES = 1L or 2L or 4L or 32L or 64L or 512L

        private val clients = ConcurrentHashMap<String, AtvRemote>()

        /** One shared connection per TV, so the home screen and the remote reuse it. */
        fun of(host: String, identity: AtvIdentity): AtvRemote = clients.getOrPut(host) { AtvRemote(host, identity) }
    }
}
