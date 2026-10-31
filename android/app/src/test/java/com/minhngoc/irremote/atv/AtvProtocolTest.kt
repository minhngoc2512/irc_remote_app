package com.minhngoc.irremote.atv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

class AtvProtocolTest {
    @Test
    fun protoRoundTrip() {
        val bytes = ProtoWriter().varint(1, 2).varint(2, 200)
            .message(20) { message(1) { varint(1, 3); varint(2, 6) }; varint(3, 1) }.toByteArray()
        val msg = ProtoMessage.parse(bytes)
        assertEquals(200L, msg.long(2))
        assertEquals(6L, msg.message(20)!!.message(1)!!.long(2))
    }

    @Test
    fun identityIsAValidCertificate() {
        val dir = Files.createTempDirectory("atv").toFile()
        val id = AtvIdentity.load(dir)
        id.certificate.verify(id.certificate.publicKey) // self-signed signature checks out
        val again = AtvIdentity.load(dir)
        assertArrayEquals(id.certificate.encoded, again.certificate.encoded)
    }

    /** Talks to a real TV: ATV_HOST=192.168.x.y ./gradlew testDebugUnitTest. The TV briefly shows a code. */
    @Test
    fun pairingReachesCodeStep() {
        val host = System.getenv("ATV_HOST")
        assumeTrue("set ATV_HOST to probe a real TV", !host.isNullOrBlank())
        val id = AtvIdentity.load(Files.createTempDirectory("atv").toFile())
        AtvPairing(host!!, id).use { it.start("IR Remote test") }
    }
}

/** Control session against a real TV with an identity copied from a paired phone: ATV_HOST=... ATV_ID_DIR=... */
class AtvControlProbe {
    @Test
    fun appClientConnects() {
        val host = System.getenv("ATV_HOST")
        val dir = System.getenv("ATV_ID_DIR")
        assumeTrue(!host.isNullOrBlank() && !dir.isNullOrBlank())
        val remote = AtvRemote(host!!, AtvIdentity.load(java.io.File(dir!!)))
        remote.connect()
        val deadline = System.currentTimeMillis() + 10_000
        while (remote.state.value != AtvRemote.State.Connected && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertEquals("state (last error: ${remote.lastError.value})", AtvRemote.State.Connected, remote.state.value)
        Thread.sleep(1_000)
        println("volume=${remote.volume.value}")
        remote.disconnect()
    }

    @Test
    fun controlSession() {
        val host = System.getenv("ATV_HOST")
        val dir = System.getenv("ATV_ID_DIR")
        assumeTrue(!host.isNullOrBlank() && !dir.isNullOrBlank())
        val id = AtvIdentity.load(java.io.File(dir!!))
        val s = id.sslContext.socketFactory.createSocket() as javax.net.ssl.SSLSocket
        s.connect(java.net.InetSocketAddress(host, 6466), 4000)
        s.soTimeout = 8000
        s.startHandshake()
        println("handshake ok: ${s.session.protocol} ${s.session.cipherSuite}")
        val input = s.inputStream
        val out = s.outputStream
        val end = System.currentTimeMillis() + 15_000
        var sentKey = false
        while (System.currentTimeMillis() < end) {
            val raw = try { input.readFrame() } catch (e: Exception) { println("read ended: $e"); break }
            val msg = ProtoMessage.parse(raw)
            val fields = (1..100).filter { msg.has(it) }
            println("<- fields=$fields bytes=${raw.joinToString("") { "%02x".format(it) }.take(120)}")
            when {
                msg.has(1) -> {
                    val supported = msg.message(1)?.long(1) ?: 0
                    println("   TV features=$supported")
                    out.writeFrame(ProtoWriter().message(1) {
                        varint(1, 615L and supported)
                        message(2) { string(1, "Probe"); string(2, "Test"); varint(3, 1); string(4, "1"); string(5, "atvremote"); string(6, "1.0.0") }
                    }.toByteArray())
                }
                msg.has(2) -> out.writeFrame(ProtoWriter().message(2) { varint(1, 615) }.toByteArray())
                msg.has(8) -> out.writeFrame(ProtoWriter().message(9) { varint(1, msg.message(8)?.long(1) ?: 0) }.toByteArray())
            }
            if (msg.has(2) && !sentKey) {
                sentKey = true
                out.writeFrame(ProtoWriter().message(10) { varint(1, 20); varint(2, 3) }.toByteArray()) // DPAD_DOWN, SHORT
                println("-> sent DPAD_DOWN")
            }
        }
        s.close()
    }
}
