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

    /** ICMP round trip using the system ping binary (no special permission needed). */
    fun ping(host: String, timeoutSec: Int = 1): Float? = runCatching {
        val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "$timeoutSec", host).redirectErrorStream(true).start()
        if (!p.waitFor(timeoutSec + 2L, TimeUnit.SECONDS)) {
            p.destroy()
            return null
        }
        TIME.find(p.inputStream.bufferedReader().readText())?.groupValues?.get(1)?.toFloat()
    }.getOrNull()

    /** Samples [host] every [periodMs] while collected. */
    fun monitor(host: String, periodMs: Long = 3_000): Flow<Float?> = flow {
        while (true) {
            emit(ping(host))
            delay(periodMs)
        }
    }.flowOn(Dispatchers.IO)
}
