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
