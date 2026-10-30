package com.minhngoc.irremote.atv

import android.annotation.SuppressLint
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * The app's client certificate for Android TV Remote v2. The TV remembers this
 * certificate after pairing, so it is created once and reused for every TV.
 */
class AtvIdentity private constructor(val certificate: X509Certificate, private val key: PrivateKey) {

    val publicKey: RSAPublicKey get() = certificate.publicKey as RSAPublicKey

    /** TLS context presenting our certificate; TVs use self-signed server certificates, so any is accepted. */
    val sslContext: SSLContext by lazy {
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setKeyEntry("atv", key, PASSWORD, arrayOf(certificate))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, PASSWORD) }
        SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(TrustAnyServer), null) }
    }

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private object TrustAnyServer : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private val PASSWORD = "irremote".toCharArray()

        /** Loads the stored identity or creates one (RSA 2048, self-signed, 10 years). Call off the main thread. */
        fun load(dir: File): AtvIdentity {
            val file = File(dir, "atv_identity.p12")
            if (file.exists()) {
                runCatching {
                    val ks = KeyStore.getInstance("PKCS12").apply { file.inputStream().use { load(it, PASSWORD) } }
                    return AtvIdentity(ks.getCertificate("atv") as X509Certificate, ks.getKey("atv", PASSWORD) as PrivateKey)
                }
            }
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val der = selfSignedCertificate(pair.public.encoded, pair.private, "atvremote")
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
            val ks = KeyStore.getInstance("PKCS12").apply {
                load(null)
                setKeyEntry("atv", pair.private, PASSWORD, arrayOf(cert))
            }
            val tmp = File(dir, "atv_identity.p12.tmp")
            tmp.outputStream().use { ks.store(it, PASSWORD) }
            tmp.renameTo(file)
            return AtvIdentity(cert, pair.private)
        }

        // --- Minimal DER encoding of an X.509 v3 certificate (RFC 5280) -------------------

        private fun selfSignedCertificate(spki: ByteArray, key: PrivateKey, commonName: String): ByteArray {
            val sha256WithRsa = seq(oid(1, 2, 840, 113549, 1, 1, 11), byteArrayOf(0x05, 0x00))
            val name = seq(set(seq(oid(2, 5, 4, 3), tlv(0x0C, commonName.toByteArray()))))
            val now = System.currentTimeMillis()
            val validity = seq(utcTime(Date(now - TimeUnit.DAYS.toMillis(1))), utcTime(Date(now + TimeUnit.DAYS.toMillis(3650))))
            val tbs = seq(
                tlv(0xA0, integer(BigInteger.valueOf(2))), // version: v3
                integer(BigInteger.valueOf(now)),
                sha256WithRsa,
                name,
                validity,
                name,
                spki,
            )
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(key)
                update(tbs)
                sign()
            }
            return seq(tbs, sha256WithRsa, tlv(0x03, byteArrayOf(0) + signature))
        }

        private fun tlv(tag: Int, value: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(tag)
            when {
                value.size < 0x80 -> out.write(value.size)
                value.size < 0x100 -> { out.write(0x81); out.write(value.size) }
                else -> { out.write(0x82); out.write(value.size shr 8); out.write(value.size and 0xFF) }
            }
            out.write(value)
            return out.toByteArray()
        }

        private fun seq(vararg parts: ByteArray) = tlv(0x30, parts.fold(ByteArray(0)) { a, b -> a + b })
        private fun set(vararg parts: ByteArray) = tlv(0x31, parts.fold(ByteArray(0)) { a, b -> a + b })
        private fun integer(v: BigInteger) = tlv(0x02, v.toByteArray())

        private fun oid(vararg arcs: Long): ByteArray {
            val out = ByteArrayOutputStream()
            out.write((arcs[0] * 40 + arcs[1]).toInt())
            for (arc in arcs.drop(2)) {
                val chunks = mutableListOf<Int>()
                var v = arc
                do {
                    chunks.add((v and 0x7F).toInt())
                    v = v shr 7
                } while (v > 0)
                chunks.reversed().forEachIndexed { i, c -> out.write(if (i < chunks.size - 1) c or 0x80 else c) }
            }
            return tlv(0x06, out.toByteArray())
        }

        private fun utcTime(date: Date): ByteArray {
            val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            return tlv(0x17, fmt.format(date).toByteArray())
        }
    }
}
