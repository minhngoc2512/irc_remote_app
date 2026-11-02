package com.minhngoc.irremote.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.TimeUnit

/** One latency sample: round-trip time in ms, or null when the host did not answer. */
object Latency {
    private val TIME = Regex("""time[=<]([0-9.]+)\s*ms""")

    /**
     * ICMP round trip using the system ping binary (no special permission needed).
     *
     * Sends a short burst and keeps the best reply: after a few idle seconds the Wi-Fi radio is in
     * power-save, so the first reply waits for the next beacon (~100 ms extra). Key presses don't
     * pay that cost (sending wakes the radio), so the minimum is the figure that matches the remote.
     */
    fun ping(host: String, count: Int = 3, timeoutSec: Int = 1): Float? = runCatching {
        val p = ProcessBuilder("/system/bin/ping", "-c", "$count", "-i", "0.2", "-W", "$timeoutSec", host)
            .redirectErrorStream(true).start()
        if (!p.waitFor(timeoutSec + count + 2L, TimeUnit.SECONDS)) {
            p.destroy()
            return null
        }
        TIME.findAll(p.inputStream.bufferedReader().readText()).minOfOrNull { it.groupValues[1].toFloat() }
    }.getOrNull()

    /** Samples [host] every [periodMs] while collected. */
    fun monitor(host: String, periodMs: Long = 3_000): Flow<Float?> = flow {
        while (true) {
            emit(ping(host))
            delay(periodMs)
        }
    }.flowOn(Dispatchers.IO)
}
