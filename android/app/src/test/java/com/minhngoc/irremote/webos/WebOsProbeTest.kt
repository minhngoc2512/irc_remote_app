package com.minhngoc.irremote.webos

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/** File-backed store so a paired test client keeps its key between runs. */
private class FileStore(private val file: File) : WebOsStore {
    private val props = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }
    private fun save() = file.outputStream().use { props.store(it, null) }
    override fun clientKey(host: String) = props.getProperty("key.$host")
    override fun saveClientKey(host: String, key: String) { props.setProperty("key.$host", key); save() }
    override fun mac(host: String) = props.getProperty("mac.$host")
    override fun saveMac(host: String, mac: String) { props.setProperty("mac.$host", mac); save() }
}

/** Live probe: WEBOS_HOST=192.168.x.y WEBOS_STORE=/path/file ./gradlew testDebugUnitTest. Accept the prompt on the TV. */
class WebOsProbeTest {
    /** Reconnects with the stored key (no prompt) and wiggles the cursor through the pointer socket. */
    @Test
    fun pointerMoves() {
        val host = System.getenv("WEBOS_HOST")
        val storePath = System.getenv("WEBOS_STORE")
        assumeTrue(!host.isNullOrBlank() && !storePath.isNullOrBlank() && System.getenv("WEBOS_POINTER") != null)
        val client = WebOsClient(host!!, FileStore(File(storePath!!)))
        client.acquire()
        val deadline = System.currentTimeMillis() + 15_000
        while (client.state.value != WebOsClient.State.Connected && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertEquals("state (error ${client.lastError.value})", WebOsClient.State.Connected, client.state.value)
        Thread.sleep(1_500) // pointer socket opens after registration
        val sent = (1..20).map { client.move(15, 0) } + (1..20).map { client.move(-15, 0) }
        println("pointer frames accepted: ${sent.count { it }}/${sent.size}")
        org.junit.Assert.assertTrue(sent.all { it })
        client.release()
        client.disconnect()
    }

    @Test
    fun pairAndRead() {
        val host = System.getenv("WEBOS_HOST")
        val storePath = System.getenv("WEBOS_STORE")
        assumeTrue(!host.isNullOrBlank() && !storePath.isNullOrBlank())
        val client = WebOsClient(host!!, FileStore(File(storePath!!)))
        client.acquire()
        val deadline = System.currentTimeMillis() + 60_000
        var last: WebOsClient.State? = null
        while (System.currentTimeMillis() < deadline) {
            val st = client.state.value
            if (st != last) { println("state=$st error=${client.lastError.value}"); last = st }
            if (st == WebOsClient.State.Connected || st == WebOsClient.State.Rejected || st == WebOsClient.State.Unreachable) break
            Thread.sleep(200)
        }
        assertEquals("last error: ${client.lastError.value}", WebOsClient.State.Connected, client.state.value)
        Thread.sleep(3_000)
        println("model=${client.modelName.value} volume=${client.volume.value} foreground=${client.foregroundApp.value}")
        println("apps=${client.apps.value.size}: ${client.apps.value.take(12).joinToString { "${it.title}(${it.id})" }}")
        println("mac=${FileStore(File(storePath)).mac(host)}")
        val inputs = kotlinx.coroutines.runBlocking { client.inputs() }
        println("inputs=${inputs.joinToString { "${it.label}(${it.id})" }}")
        client.toast("IR Remote đã kết nối")
        Thread.sleep(1_500)
        client.release()
        client.disconnect()
    }
}
