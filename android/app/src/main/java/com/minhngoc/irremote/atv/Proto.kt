package com.minhngoc.irremote.atv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal protobuf wire format, enough for the Android TV Remote v2 messages
 * (polo.proto for pairing, remotemessage.proto for control). Field numbers in
 * the callers are taken from those .proto files.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long) = apply {
        tag(field, 0)
        writeVarint(out, value)
    }

    fun bool(field: Int, value: Boolean) = varint(field, if (value) 1 else 0)

    fun bytes(field: Int, value: ByteArray) = apply {
        tag(field, 2)
        writeVarint(out, value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, build: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = writeVarint(out, ((field shl 3) or wireType).toLong())
}

/** Decoded message: field number -> values (Long for varints, ByteArray for length-delimited). */
class ProtoMessage(private val fields: Map<Int, List<Any>>) {
    fun has(field: Int) = fields.containsKey(field)
    fun long(field: Int, default: Long = 0): Long = fields[field]?.firstOrNull() as? Long ?: default
    fun int(field: Int, default: Int = 0): Int = long(field, default.toLong()).toInt()
    fun bool(field: Int): Boolean = long(field) != 0L
    fun bytes(field: Int): ByteArray? = fields[field]?.firstOrNull() as? ByteArray
    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)
    fun message(field: Int): ProtoMessage? = bytes(field)?.let { parse(it) }

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = mutableMapOf<Int, MutableList<Any>>()
            var pos = 0
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    val b = data[pos++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            while (pos < data.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                val value: Any = when ((key and 7).toInt()) {
                    0 -> varint()
                    1 -> data.copyOfRange(pos, pos + 8).also { pos += 8 }
                    2 -> varint().toInt().let { len -> data.copyOfRange(pos, pos + len).also { pos += len } }
                    5 -> data.copyOfRange(pos, pos + 4).also { pos += 4 }
                    else -> throw IllegalArgumentException("unsupported wire type in field $field")
                }
                fields.getOrPut(field) { mutableListOf() }.add(value)
            }
            return ProtoMessage(fields)
        }
    }
}

internal fun writeVarint(out: OutputStream, value: Long) {
    var v = value
    while (v and 0x7FL.inv() != 0L) {
        out.write(((v and 0x7F) or 0x80).toInt())
        v = v ushr 7
    }
    out.write(v.toInt())
}

/** Messages on both ports are framed as varint length + body. */
internal fun OutputStream.writeFrame(body: ByteArray) {
    val frame = ByteArrayOutputStream(body.size + 5)
    writeVarint(frame, body.size.toLong())
    frame.write(body)
    write(frame.toByteArray())
    flush()
}

internal fun InputStream.readFrame(): ByteArray {
    var len = 0L
    var shift = 0
    while (true) {
        val b = read()
        if (b < 0) throw EOFException("connection closed")
        len = len or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) break
        shift += 7
    }
    val body = ByteArray(len.toInt())
    var read = 0
    while (read < body.size) {
        val n = read(body, read, body.size - read)
        if (n < 0) throw EOFException("connection closed")
        read += n
    }
    return body
}
