package com.minhngoc.irremote.webos

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Wake-on-LAN magic packet: 6 x 0xFF followed by the MAC address 16 times, sent as UDP broadcast. */
object WakeOnLan {
    fun send(mac: String, lastKnownHost: String? = null) {
        val bytes = mac.split(':', '-').map { it.toInt(16).toByte() }
        require(bytes.size == 6) { "bad MAC $mac" }
        val packet = ByteArray(6) { 0xFF.toByte() } + (1..16).flatMap { bytes }.toByteArray()
        // Global broadcast, plus the /24 broadcast of the TV's last address for routers that drop the former.
        val targets = buildList {
            add("255.255.255.255")
            lastKnownHost?.substringBeforeLast('.', "")?.takeIf { it.isNotEmpty() }?.let { add("$it.255") }
        }
        DatagramSocket().use { socket ->
            socket.broadcast = true
            repeat(3) {
                for (target in targets) for (port in intArrayOf(9, 7)) {
                    socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(target), port))
                }
            }
        }
    }
}
