// Little-endian widths and bit masks of the Norito wire format.
// Infix bit operations keep explicit grouping.
@file:Suppress("MagicNumber", "UnnecessaryParentheses")

package jp.co.soramitsu.irohaconnect.impl.protocol

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Little-endian Norito building blocks shared by the Connect frames and the transaction payloads.
 *
 * Connect frames and envelopes prefix every field with a fixed u64 length. Transaction payloads use canonical
 * unsigned LEB128 ("compact") lengths instead, except the transfer instruction's frame container, which keeps a u64.
 * Counts of vectors stay u64 in both layouts.
 */
internal object NoritoBytes {
    const val ELEMENT_FRAME_BYTES = 9

    fun byteOf(value: Int): ByteArray = byteArrayOf(value.toByte())

    fun u16(value: Int): ByteArray {
        require(value in 0..0xffff) { "u16 out of range" }
        return byteArrayOf(value.toByte(), (value ushr 8).toByte())
    }

    fun u32(value: Long): ByteArray {
        require(value in 0..0xffff_ffffL) { "u32 out of range" }
        return ByteArray(4) { index -> (value ushr (8 * index)).toByte() }
    }

    /** [value] is read as an unsigned 64-bit integer. */
    fun u64(value: Long): ByteArray = ByteArray(8) { index -> (value ushr (8 * index)).toByte() }

    fun concat(vararg chunks: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(chunks.sumOf { it.size })
        chunks.forEach(output::write)
        return output.toByteArray()
    }

    fun concat(chunks: List<ByteArray>): ByteArray {
        val output = ByteArrayOutputStream(chunks.sumOf { it.size })
        chunks.forEach(output::write)
        return output.toByteArray()
    }

    /** A Connect field: u64 length, then the bytes. */
    fun field(payload: ByteArray): ByteArray = concat(u64(payload.size.toLong()), payload)

    fun struct(fields: List<ByteArray>): ByteArray = concat(fields.map(::field))

    /** A Connect enum value: u32 tag, then the body as one field. */
    fun tagged(tag: Long, body: ByteArray): ByteArray = concat(u32(tag), field(body))

    /** A `[u8; N]` the way Torii encodes it: every byte as its own one-byte field (9 bytes per byte). */
    fun elementFramed(bytes: ByteArray): ByteArray {
        val output = ByteArray(bytes.size * ELEMENT_FRAME_BYTES)
        bytes.forEachIndexed { index, byte ->
            val offset = index * ELEMENT_FRAME_BYTES
            output[offset] = 1
            output[offset + 8] = byte
        }
        return output
    }

    fun compactLength(value: Long): ByteArray {
        require(value >= 0) { "compact length must be non-negative" }
        val output = ByteArrayOutputStream(10)
        var remaining = value
        do {
            var next = (remaining and 0x7f).toInt()
            remaining = remaining ushr 7
            if (remaining != 0L) next = next or 0x80
            output.write(next)
        } while (remaining != 0L)
        return output.toByteArray()
    }

    /** A transaction-payload field: canonical LEB128 length, then the bytes. */
    fun compactField(payload: ByteArray): ByteArray = concat(compactLength(payload.size.toLong()), payload)

    fun compactStruct(fields: List<ByteArray>): ByteArray = concat(fields.map(::compactField))

    fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean = MessageDigest.isEqual(left, right)
}

/** A bounds-checked little-endian reader. [fail] decides which exception a malformed input raises. */
internal class NoritoReader(
    private val bytes: ByteArray,
    private val label: String,
    private val compactLengths: Boolean,
    private val fail: (String) -> Nothing
) {
    private var offset = 0

    val remaining: Int get() = bytes.size - offset

    fun readBytes(length: Long, name: String): ByteArray {
        if (length < 0 || length > remaining) fail("$label.$name is truncated")
        val output = bytes.copyOfRange(offset, offset + length.toInt())
        offset += length.toInt()
        return output
    }

    fun readU8(name: String): Int = readBytes(1, name)[0].toInt() and 0xff

    fun readU16(name: String): Int {
        val value = readBytes(2, name)
        return (value[0].toInt() and 0xff) or ((value[1].toInt() and 0xff) shl 8)
    }

    fun readU32(name: String): Long {
        val value = readBytes(4, name)
        var result = 0L
        for (index in 3 downTo 0) result = (result shl 8) or (value[index].toLong() and 0xff)
        return result
    }

    /** The raw unsigned 64-bit value; compare with [java.lang.Long.compareUnsigned] when it matters. */
    fun readU64(name: String): Long {
        val value = readBytes(8, name)
        var result = 0L
        for (index in 7 downTo 0) result = (result shl 8) or (value[index].toLong() and 0xff)
        return result
    }

    /** A u64 that must fit a non-negative Long. */
    fun readU64Length(name: String): Long {
        val value = readU64(name)
        if (value < 0) fail("$label.$name is too large")
        return value
    }

    @Suppress("NestedBlockDepth") // One canonical-LEB128 loop, as iroha-js Reader.readLength.
    private fun readCompactLength(name: String): Long {
        val start = offset
        var value = 0L
        var shift = 0
        for (index in 0 until MAX_COMPACT_BYTES) {
            val byte = readU8(name)
            if (shift == 63 && (byte and 0x7e) != 0) fail("$label.$name length prefix is invalid")
            value = value or ((byte and 0x7f).toLong() shl shift)
            if ((byte and 0x80) == 0) {
                if (value < 0) fail("$label.$name is too large")
                if (offset - start != NoritoBytes.compactLength(value).size) {
                    fail("$label.$name length is not canonical")
                }
                return value
            }
            shift += 7
        }
        fail("$label.$name length prefix is invalid")
    }

    fun readField(name: String, maximum: Long = Long.MAX_VALUE): ByteArray {
        val length = if (compactLengths) readCompactLength("$name.length") else readU64Length("$name.length")
        if (length > maximum) fail("$label.$name exceeds $maximum bytes")
        return readBytes(length, name)
    }

    /** The transfer instruction's frame container: always a u64 length, even inside a compact payload. */
    fun readFieldU64(name: String): ByteArray = readBytes(readU64Length("$name.length"), name)

    fun done() {
        if (remaining != 0) fail("$label has trailing bytes")
    }

    private companion object {
        const val MAX_COMPACT_BYTES = 10
    }
}

internal object IrohaConnectEncoding {
    private const val HEX_DIGITS = "0123456789abcdef"
    private val BASE64URL_PATTERN = Regex("^[A-Za-z0-9_-]+$")

    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

    fun unhex(value: String): ByteArray {
        val text = value.removePrefix("0x").removePrefix("0X")
        require(text.length % 2 == 0 && text.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "not hex" }
        return ByteArray(text.length / 2) { index -> text.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    fun toBase64Url(bytes: ByteArray): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** Strict unpadded base64url: returns null unless [value] is the canonical spelling of its bytes. */
    fun fromCanonicalBase64Url(value: String): ByteArray? {
        if (!BASE64URL_PATTERN.matches(value)) return null
        val decoded = runCatching { java.util.Base64.getUrlDecoder().decode(value) }.getOrNull() ?: return null
        return decoded.takeIf { toBase64Url(it) == value }
    }
}
