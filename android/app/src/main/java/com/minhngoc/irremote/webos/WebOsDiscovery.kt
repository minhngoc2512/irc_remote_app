package com.minhngoc.irremote.webos

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL

data class FoundWebOs(val name: String, val host: String)

/**
 * Finds LG webOS TVs two ways at once: an SSDP search for the "second screen" service,
 * and a sweep of the phone's /24 for hosts with both webOS ports (3000 and 3001) open.
 * Some phones (HyperOS) never deliver the SSDP replies, so the sweep is the safety net.
 */
object WebOsDiscovery {
    private const val ST = "urn:lge-com:service:webos-second-screen:1"
    private const val TAG = "WebOsDiscovery"

    fun scan(context: Context): Flow<List<FoundWebOs>> = channelFlow {
        val found = linkedMapOf<String, FoundWebOs>()
        val report: suspend (FoundWebOs) -> Unit = { tv ->
            val changed = synchronized(found) {
                val known = found[tv.host]
                // An SSDP name beats the placeholder from the port sweep.
                if (known == null || known.name.startsWith("TV LG (") && !tv.name.startsWith("TV LG (")) {
                    found[tv.host] = tv
                    true
                } else false
            }
            if (changed) send(synchronized(found) { found.values.toList() })
        }
        send(emptyList())
        coroutineScope {
            launch { ssdp(context, report) }
            launch { sweep(context, report) }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun ProducerScope<List<FoundWebOs>>.ssdp(context: Context, report: suspend (FoundWebOs) -> Unit) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("webos-ssdp").apply { setReferenceCounted(false) }
        lock.acquire()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val search = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\nST: $ST\r\n\r\n").toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                val buf = ByteArray(2048)
                repeat(4) {
                    socket.send(DatagramPacket(search, search.size, group, 1900))
                    val until = System.currentTimeMillis() + 1_500
                    while (System.currentTimeMillis() < until) {
                        val packet = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(packet)
                        } catch (e: SocketTimeoutException) {
                            continue
                        }
                        val text = String(packet.data, 0, packet.length)
                        if (!text.contains(ST, ignoreCase = true)) continue
                        val host = packet.address.hostAddress ?: continue
                        val location = Regex("(?im)^location:\\s*(\\S+)").find(text)?.groupValues?.get(1)
                        report(FoundWebOs(friendlyName(location) ?: "LG webOS TV", host))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSDP failed: ${e.message}")
        } finally {
            lock.release()
        }
    }

    /** Probes every address of the phone's /24 for webOS's two WebSocket ports. */
    private suspend fun sweep(context: Context, report: suspend (FoundWebOs) -> Unit) {
        val own = ownIpv4(context) ?: run {
            Log.w(TAG, "no IPv4 address to sweep")
            return
        }
        val prefix = own.substringBeforeLast('.')
        val gate = Semaphore(48)
        coroutineScope {
            for (i in 1..254) {
                val host = "$prefix.$i"
                if (host == own) continue
                launch {
                    gate.withPermit {
                        if (open(host, 3001) && open(host, 3000)) report(FoundWebOs("TV LG ($host)", host))
                    }
                }
            }
        }
    }

    private fun open(host: String, port: Int): Boolean =
        runCatching { Socket().use { it.connect(InetSocketAddress(host, port), 400) } }.isSuccess

    private fun ownIpv4(context: Context): String? {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
    }

    private fun friendlyName(location: String?): String? = location?.let {
        runCatching {
            val xml = URL(it).openStream().use { s -> s.readBytes().toString(Charsets.UTF_8) }
            Regex("<friendlyName>([^<]+)</friendlyName>").find(xml)?.groupValues?.get(1)?.trim()
        }.getOrNull()
    }
}
