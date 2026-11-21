package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Thrown when bytes are not the SCALE encoding this package expects. Nothing derived from them is used. */
class MetadataDecodeException(message: String) : IllegalArgumentException(message)

private const val BYTE_MASK = 0xff
private const val BYTE_BITS = 8
private const val COMPACT_MODE_MASK = 0b11
private const val COMPACT_SINGLE = 0b00
private const val COMPACT_TWO = 0b01
private const val COMPACT_FOUR = 0b10
private const val COMPACT_SHIFT = 2
private const val COMPACT_SINGLE_LIMIT = 1L shl 6
private const val COMPACT_TWO_LIMIT = 1L shl 14
private const val COMPACT_FOUR_LIMIT = 1L shl 30
private const val U32_MAX = 0xffff_ffffL
private const val U16_BYTES = 2
private const val U32_BYTES = 4
private const val BIG_COMPACT_U32_PREFIX = 0b11

/**
 * A strict SCALE reader with the semantics of parity-scale-codec: compact integers must use their canonical (shortest)
 * form, strings must be valid UTF-8, and options and booleans accept only 0 and 1.
 */
internal class ScaleInput(private val bytes: ByteArray) {
    private var position = 0

    val remaining: Int
        get() = bytes.size - position

    fun readU8(): Int {
        if (position >= bytes.size) throw MetadataDecodeException("Unexpected end of input")
        return bytes[position++].toInt() and BYTE_MASK
    }

    fun readU16(): Int = readLittleEndian(U16_BYTES).toInt()

    fun readU32(): Long = readLittleEndian(U32_BYTES)

    fun readBytes(count: Int): ByteArray {
        if (count < 0 || count > remaining) throw MetadataDecodeException("Unexpected end of input")
        val result = bytes.copyOfRange(position, position + count)
        position += count
        return result
    }

    /** A `Compact<u32>`, rejected unless it is the canonical encoding parity-scale-codec accepts. */
    fun readCompactU32(): Long {
        val first = readU8()
        return when (first and COMPACT_MODE_MASK) {
            COMPACT_SINGLE -> (first shr COMPACT_SHIFT).toLong()
            COMPACT_TWO -> {
                val value = (first or (readU8() shl BYTE_BITS)).toLong() shr COMPACT_SHIFT
                if (value < COMPACT_SINGLE_LIMIT) throw MetadataDecodeException("Non-canonical compact integer")
                value
            }
            COMPACT_FOUR -> {
                val value = ((readLittleEndian(U32_BYTES - 1) shl BYTE_BITS) or first.toLong()) shr COMPACT_SHIFT
                if (value < COMPACT_TWO_LIMIT) throw MetadataDecodeException("Non-canonical compact integer")
                value
            }
            else -> {
                // Big-integer mode: only four following bytes fit a u32.
                if (first shr COMPACT_SHIFT != 0) throw MetadataDecodeException("Compact integer exceeds u32")
                val value = readLittleEndian(U32_BYTES)
                if (value < COMPACT_FOUR_LIMIT) throw MetadataDecodeException("Non-canonical compact integer")
                value
            }
        }
    }

    /** A collection length, bounded by the bytes left so a forged length cannot allocate more than the input holds. */
    fun readLength(): Int {
        val length = readCompactU32()
        if (length > remaining) throw MetadataDecodeException("Length exceeds the remaining input")
        return length.toInt()
    }

    fun readString(): String {
        val data = readBytes(readLength())
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data))
                .toString()
        } catch (error: CharacterCodingException) {
            throw MetadataDecodeException("Invalid UTF-8 string")
        }
    }

    fun readByteVec(): ByteArray = readBytes(readLength())

    fun readBool(): Boolean = when (readU8()) {
        0 -> false
        1 -> true
        else -> throw MetadataDecodeException("Invalid boolean")
    }

    fun <T> readOption(read: ScaleInput.() -> T): T? = when (readU8()) {
        0 -> null
        1 -> read()
        else -> throw MetadataDecodeException("Invalid option")
    }

    fun <T> readVec(read: ScaleInput.() -> T): List<T> {
        val count = readLength()
        return List(count) { read() }
    }

    fun requireEnd() {
        if (remaining != 0) throw MetadataDecodeException("Unexpected trailing bytes")
    }

    private fun readLittleEndian(byteCount: Int): Long {
        var value = 0L
        for (index in 0 until byteCount) {
            value = value or (readU8().toLong() shl (BYTE_BITS * index))
        }
        return value
    }
}

/** SCALE writer for the types RFC 78 hashes. */
internal class ScaleOutput {
    private val buffer = ByteArrayOutputStream()

    fun writeU8(value: Int): ScaleOutput = apply { buffer.write(value and BYTE_MASK) }

    fun writeU16(value: Int): ScaleOutput = writeLittleEndian(value.toLong(), U16_BYTES)

    fun writeU32(value: Long): ScaleOutput = writeLittleEndian(value, U32_BYTES)

    fun writeBool(value: Boolean): ScaleOutput = writeU8(if (value) 1 else 0)

    fun writeBytes(value: ByteArray): ScaleOutput = apply { buffer.write(value) }

    fun writeCompactU32(value: Long): ScaleOutput = apply {
        require(value in 0..U32_MAX) { "Compact<u32> out of range" }
        when {
            value < COMPACT_SINGLE_LIMIT -> writeU8((value shl COMPACT_SHIFT).toInt())
            value < COMPACT_TWO_LIMIT -> writeLittleEndian((value shl COMPACT_SHIFT) or COMPACT_TWO.toLong(), U16_BYTES)
            value < COMPACT_FOUR_LIMIT -> writeLittleEndian((value shl COMPACT_SHIFT) or COMPACT_FOUR.toLong(), U32_BYTES)
            else -> writeU8(BIG_COMPACT_U32_PREFIX).writeLittleEndian(value, U32_BYTES)
        }
    }

    fun writeString(value: String): ScaleOutput {
        val data = value.toByteArray(StandardCharsets.UTF_8)
        return writeCompactU32(data.size.toLong()).writeBytes(data)
    }

    fun writeOptionString(value: String?): ScaleOutput = if (value == null) writeU8(0) else writeU8(1).writeString(value)

    fun <T> writeVec(values: List<T>, write: ScaleOutput.(T) -> Unit): ScaleOutput = apply {
        writeCompactU32(values.size.toLong())
        values.forEach { write(it) }
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()

    private fun writeLittleEndian(value: Long, byteCount: Int): ScaleOutput = apply {
        for (index in 0 until byteCount) {
            buffer.write(((value shr (BYTE_BITS * index)) and BYTE_MASK.toLong()).toInt())
        }
    }
}
