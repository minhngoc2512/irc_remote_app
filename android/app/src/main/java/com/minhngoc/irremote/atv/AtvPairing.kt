package com.minhngoc.irremote.atv

import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class PairingException(message: String) : IOException(message)

/**
 * Pairing on port 6467 (polo.proto). [start] makes the TV show a 6-character
 * hex code; [finish] proves we saw it. Blocking: call from a background thread.
 *
 * Every message is an OuterMessage { protocol_version=1: 2, status=2: 200, <payload> }.
 */
class AtvPairing(private val host: String, private val identity: AtvIdentity, private val port: Int = 6467) : Closeable {
    private var socket: SSLSocket? = null

    fun start(clientName: String) {
        socket = connectWithRetry()
        send { message(10) { string(1, "atvremote"); string(2, clientName) } } // pairing_request
        expect(11) // pairing_request_ack
        send { // options: we type the code (input role), 6 hex symbols
            message(20) {
                message(1) { varint(1, ENCODING_HEX); varint(2, 6) }
                varint(3, ROLE_INPUT)
            }
        }
        expect(20) // options
        send { message(30) { message(1) { varint(1, ENCODING_HEX); varint(2, 6) }; varint(2, ROLE_INPUT) } } // configuration
        expect(31) // configuration_ack: the TV now shows the code
    }

    /** The TV's pairing service briefly refuses connections right after one was dropped. */
    private fun connectWithRetry(): SSLSocket {
        var last: java.net.ConnectException? = null
        repeat(4) { attempt ->
            if (attempt > 0) Thread.sleep(1_500)
            val s = identity.sslContext.socketFactory.createSocket() as SSLSocket
            try {
                s.connect(InetSocketAddress(host, port), 5_000)
                s.soTimeout = 60_000
                s.startHandshake()
                return s
            } catch (e: java.net.ConnectException) {
                runCatching { s.close() }
                last = e
            }
        }
        throw last!!
    }

    fun finish(code: String) {
        val s = socket ?: throw PairingException("Chưa bắt đầu ghép nối")
        val pin = code.trim().uppercase()
        if (!pin.matches(Regex("[0-9A-F]{6}"))) throw PairingException("Mã gồm 6 ký tự 0-9, A-F")
        val server = s.session.peerCertificates.first().publicKey as RSAPublicKey
        val client = identity.publicKey
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(unsigned(client.modulus))
            update(unsigned(client.publicExponent))
            update(unsigned(server.modulus))
            update(unsigned(server.publicExponent))
            update(pin.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            digest()
        }
        // The first two characters are a checksum of the rest: catches typos before the TV does.
        if ((digest[0].toInt() and 0xFF) != pin.substring(0, 2).toInt(16)) throw PairingException("Mã không đúng, kiểm tra lại trên TV")
        send { message(40) { bytes(1, digest) } } // secret
        expect(41) // secret_ack
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun send(payload: ProtoWriter.() -> Unit) {
        val body = ProtoWriter().varint(1, 2).varint(2, STATUS_OK).apply(payload).toByteArray()
        socket!!.outputStream.writeFrame(body)
    }

    private fun expect(field: Int): ProtoMessage {
        val msg = ProtoMessage.parse(socket!!.inputStream.readFrame())
        val status = msg.long(2)
        if (status != STATUS_OK) {
            throw PairingException(if (status == 402L) "Mã không đúng" else "TV từ chối ghép nối (mã lỗi $status)")
        }
        if (!msg.has(field)) throw PairingException("TV trả lời không đúng giao thức")
        return msg
    }

    private companion object {
        const val STATUS_OK = 200L
        const val ENCODING_HEX = 3L
        const val ROLE_INPUT = 1L

        /** Big-endian bytes without a sign byte, as Python's bytes.fromhex(f"{n:X}") in the reference client. */
        fun unsigned(n: BigInteger): ByteArray = n.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
    }
}
