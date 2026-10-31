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
        if (System.getenv("ATV_VOICE") != null) {
            val session = kotlinx.coroutines.runBlocking { remote.startVoice() }
            println("voice session=$session")
            org.junit.Assert.assertNotNull("TV did not open a voice session", session)
            remote.sendVoice(session!!, ByteArray(16_000)) // 1 s of silence
            remote.endVoice(session)
            Thread.sleep(1_500)
        }
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

/** Logs every message for ATV_LISTEN_SEC seconds; types "test" once the TV reports a text field. */
class AtvImeProbe {
    @Test
    fun listen() {
        val host = System.getenv("ATV_HOST")
        val dir = System.getenv("ATV_ID_DIR")
        val secs = System.getenv("ATV_LISTEN_SEC")?.toIntOrNull()
        assumeTrue(!host.isNullOrBlank() && !dir.isNullOrBlank() && secs != null)
        val id = AtvIdentity.load(java.io.File(dir!!))
        val s = id.sslContext.socketFactory.createSocket() as javax.net.ssl.SSLSocket
        s.connect(java.net.InetSocketAddress(host, 6466), 4000)
        s.soTimeout = 20_000
        s.startHandshake()
        val input = s.inputStream
        val out = s.outputStream
        val end = System.currentTimeMillis() + secs!! * 1000L
        var typed = false
        val log = java.io.File(System.getenv("ATV_LOG") ?: "/tmp/atv_ime.log")
        log.writeText("")
        fun note(line: String) { log.appendText("${System.currentTimeMillis() % 100000} $line\n") }
        while (System.currentTimeMillis() < end) {
            val raw = try { input.readFrame() } catch (e: Exception) { note("read ended: $e"); break }
            val msg = ProtoMessage.parse(raw)
            val fields = (1..100).filter { msg.has(it) }
            if (fields != listOf(8)) note("<- $fields ${raw.joinToString("") { "%02x".format(it) }.take(300)}")
            when {
                msg.has(1) -> out.writeFrame(ProtoWriter().message(1) {
                    varint(1, 623L and (msg.message(1)?.long(1) ?: 0))
                    message(2) { string(1, "Probe"); string(2, "Test"); varint(3, 1); string(4, "1"); string(5, "atvremote"); string(6, "1.0.0") }
                }.toByteArray())
                msg.has(2) -> out.writeFrame(ProtoWriter().message(2) { varint(1, 623) }.toByteArray())
                msg.has(8) -> out.writeFrame(ProtoWriter().message(9) { varint(1, msg.message(8)?.long(1) ?: 0) }.toByteArray())
                msg.has(21) && !typed -> {
                    typed = true
                    val be = msg.message(21)!!
                    note("   batch_edit ime=${be.long(1)} field=${be.long(2)} -> typing")
                    out.writeFrame(ProtoWriter().message(21) {
                        varint(1, be.long(1)); varint(2, be.long(2))
                        message(3) { varint(1, 1); message(2) { varint(1, 3); varint(2, 3); string(3, "test") } }
                    }.toByteArray())
                }
            }
        }
        s.close()
    }
}

/** Waits for the TV to open a text field, then tries text-input variants a1..e5 three seconds apart. */
class AtvImeVariants {
    @Test
    fun variants() {
        val host = System.getenv("ATV_HOST")
        val dir = System.getenv("ATV_ID_DIR")
        assumeTrue(!host.isNullOrBlank() && !dir.isNullOrBlank() && System.getenv("ATV_IME_VARIANTS") != null)
        val log = java.io.File(System.getenv("ATV_LOG") ?: "/tmp/atv_ime.log").apply { writeText("") }
        fun note(line: String) = log.appendText("${java.time.LocalTime.now()} $line\n")
        val id = AtvIdentity.load(java.io.File(dir!!))
        val s = id.sslContext.socketFactory.createSocket() as javax.net.ssl.SSLSocket
        s.connect(java.net.InetSocketAddress(host, 6466), 4000)
        s.soTimeout = 20_000
        s.startHandshake()
        val input = s.inputStream
        val out = s.outputStream
        val lock = Any()
        fun send(w: ProtoWriter) = synchronized(lock) { out.writeFrame(w.toByteArray()) }
        val counters = java.util.concurrent.atomic.AtomicLongArray(longArrayOf(-1L, 0L)) // ime, field
        val reader = Thread {
            try {
                while (true) {
                    val raw = input.readFrame()
                    val msg = ProtoMessage.parse(raw)
                    val fields = (1..100).filter { msg.has(it) }
                    if (fields != listOf(8)) note("<- $fields ${raw.joinToString("") { "%02x".format(it) }.take(240)}")
                    when {
                        msg.has(1) -> send(ProtoWriter().message(1) {
                            varint(1, 623L and (msg.message(1)?.long(1) ?: 0))
                            message(2) { string(1, "Probe"); string(2, "Test"); varint(3, 1); string(4, "1"); string(5, "atvremote"); string(6, "1.0.0") }
                        })
                        msg.has(2) -> send(ProtoWriter().message(2) { varint(1, 623) })
                        msg.has(8) -> send(ProtoWriter().message(9) { varint(1, msg.message(8)?.long(1) ?: 0) })
                        msg.has(21) -> msg.message(21)!!.let { counters.set(0, it.long(1)); counters.set(1, it.long(2)) }
                    }
                }
            } catch (e: Exception) { note("reader ended: $e") }
        }.apply { isDaemon = true; start() }
        val wait = System.currentTimeMillis() + 75_000
        while (counters.get(0) < 0 && System.currentTimeMillis() < wait) Thread.sleep(200)
        val ime = counters.get(0)
        val field = counters.get(1)
        note("text field seen: ime=$ime field=$field")
        if (ime < 0) { s.close(); return }
        fun edit(text: String, ic: Long, fc: Long, insert: Long, pos: Long) = ProtoWriter().message(21) {
            varint(1, ic); varint(2, fc)
            message(3) { varint(1, insert); message(2) { varint(1, pos); varint(2, pos); string(3, text) } }
        }
        val variants = listOf(
            "a1" to { edit("a1", ime, field, 1, 1) },     // app today (python reference)
            "b2" to { edit("b2", 0, 0, 0, 11) },          // bytes from the protocol wiki
            "c3" to { edit("c3", ime, field, 0, 11) },
            "d4" to { edit("d4", ime, field, 0, 1) },
            "e5" to { edit("e5", ime + 1, field, 1, 1) },
        )
        Thread.sleep(2_000)
        for ((name, make) in variants) {
            note("-> variant $name (ime=$ime field=$field)")
            send(make())
            Thread.sleep(3_000)
        }
        Thread.sleep(2_000)
        s.close()
        reader.join(1_000)
    }
}


/** Sends wiki-format text edits right away and logs the TV's echo of the field (remote_ime_show_request). */
class AtvImeRepeat {
    @Test
    fun repeat() {
        val host = System.getenv("ATV_HOST")
        val dir = System.getenv("ATV_ID_DIR")
        assumeTrue(!host.isNullOrBlank() && !dir.isNullOrBlank() && System.getenv("ATV_IME_REPEAT") != null)
        val log = java.io.File(System.getenv("ATV_LOG") ?: "/tmp/atv_ime.log").apply { writeText("") }
        fun note(line: String) = log.appendText("${java.time.LocalTime.now()} $line\n")
        val id = AtvIdentity.load(java.io.File(dir!!))
        val s = id.sslContext.socketFactory.createSocket() as javax.net.ssl.SSLSocket
        s.connect(java.net.InetSocketAddress(host, 6466), 4000)
        s.soTimeout = 20_000
        s.startHandshake()
        val out = s.outputStream
        val lock = Any()
        fun send(w: ProtoWriter) = synchronized(lock) { out.writeFrame(w.toByteArray()) }
        Thread {
            try {
                while (true) {
                    val raw = s.inputStream.readFrame()
                    val msg = ProtoMessage.parse(raw)
                    val fields = (1..100).filter { msg.has(it) }
                    if (msg.has(22)) {
                        val st = msg.message(22)!!.message(2)
                        note("<- show_request value='${st?.string(2)}' start=${st?.long(3)} end=${st?.long(4)} counter=${st?.long(1)}")
                    } else if (fields != listOf(8)) note("<- $fields")
                    when {
                        msg.has(1) -> send(ProtoWriter().message(1) {
                            varint(1, 623L and (msg.message(1)?.long(1) ?: 0))
                            message(2) { string(1, "Probe"); string(2, "Test"); varint(3, 1); string(4, "1"); string(5, "atvremote"); string(6, "1.0.0") }
                        })
                        msg.has(2) -> send(ProtoWriter().message(2) { varint(1, 623) })
                        msg.has(8) -> send(ProtoWriter().message(9) { varint(1, msg.message(8)?.long(1) ?: 0) })
                    }
                }
            } catch (e: Exception) { note("reader ended: $e") }
        }.apply { isDaemon = true; start() }
        Thread.sleep(2_000)
        for (text in listOf("x1", "y2", "Việt")) {
            note("-> '$text'")
            send(ProtoWriter().message(21) {
                varint(1, 0); varint(2, 0)
                message(3) { varint(1, 0); message(2) { varint(1, 11); varint(2, 11); string(3, text) } }
            })
            Thread.sleep(2_500)
        }
        s.close()
    }
}


/** Streams a recorded PCM file (8 kHz 16-bit mono) to the TV assistant in real time: ATV_VOICE_PCM=... */
class AtvVoiceReplay {
    @Test
    fun replay() {
        val host = System.getenv("ATV_HOST")
        val dir = System.getenv("ATV_ID_DIR")
        val pcmPath = System.getenv("ATV_VOICE_PCM")
        assumeTrue(!host.isNullOrBlank() && !dir.isNullOrBlank() && !pcmPath.isNullOrBlank())
        val pcm = java.io.File(pcmPath!!).readBytes()
        val remote = AtvRemote(host!!, AtvIdentity.load(java.io.File(dir!!)))
        remote.connect()
        val deadline = System.currentTimeMillis() + 10_000
        while (remote.state.value != AtvRemote.State.Connected && System.currentTimeMillis() < deadline) Thread.sleep(100)
        Thread.sleep(500)
        val session = kotlinx.coroutines.runBlocking { remote.startVoice() }
        println("session=$session")
        org.junit.Assert.assertNotNull(session)
        Thread.sleep(300)
        var i = 0
        while (i < pcm.size) { // 8 KB = 0.512 s of audio, paced like a live microphone
            remote.sendVoice(session!!, pcm.copyOfRange(i, minOf(i + 8192, pcm.size)))
            i += 8192
            Thread.sleep(512)
        }
        remote.endVoice(session!!)
        Thread.sleep(4_000)
        remote.disconnect()
    }
}
