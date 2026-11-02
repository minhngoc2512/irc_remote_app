package com.minhngoc.irremote.webos

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

data class FoundWebOs(val name: String, val host: String)

/** Finds LG webOS TVs with an SSDP search for the "second screen" service they advertise. */
object WebOsDiscovery {
    private const val ST = "urn:lge-com:service:webos-second-screen:1"

    fun scan(context: Context): Flow<List<FoundWebOs>> = flow {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("webos-ssdp").apply { setReferenceCounted(false) }
        lock.acquire()
        try {
            val found = linkedMapOf<String, FoundWebOs>()
            emit(emptyList())
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val search = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\nST: $ST\r\n\r\n").toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                val buf = ByteArray(2048)
                repeat(4) { round ->
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
                        if (host in found) continue
                        val location = Regex("(?im)^location:\\s*(\\S+)").find(text)?.groupValues?.get(1)
                        found[host] = FoundWebOs(friendlyName(location) ?: "LG webOS TV", host)
                        emit(found.values.toList())
                    }
                    if (round == 3) return@use
                }
            }
        } finally {
            lock.release()
        }
    }.flowOn(Dispatchers.IO)

    private fun friendlyName(location: String?): String? = location?.let {
        runCatching {
            val xml = URL(it).openStream().use { s -> s.readBytes().toString(Charsets.UTF_8) }
            Regex("<friendlyName>([^<]+)</friendlyName>").find(xml)?.groupValues?.get(1)?.trim()
        }.getOrNull()
    }
}
