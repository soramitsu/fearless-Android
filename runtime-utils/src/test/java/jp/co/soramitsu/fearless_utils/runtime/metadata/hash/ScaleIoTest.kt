package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ScaleIoTest {

    // parity-scale-codec Compact<u32> encodings.
    private val compacts = mapOf(
        0L to "0x00",
        63L to "0xfc",
        64L to "0x0101",
        16383L to "0xfdff",
        16384L to "0x02000100",
        (1L shl 30) - 1 to "0xfeffffff",
        1L shl 30 to "0x0300000040",
        0xffff_ffffL to "0x03ffffffff"
    )

    @Test
    fun `compact u32 round trips in its canonical form`() {
        compacts.forEach { (value, hex) ->
            assertEquals(hex, ScaleOutput().writeCompactU32(value).toByteArray().toHexString(true))
            val input = ScaleInput(hex.fromHex())
            assertEquals(value, input.readCompactU32())
            input.requireEnd()
        }
    }

    @Test
    fun `non-canonical and oversized compact integers are rejected`() {
        listOf("0x0100", "0x02000000", "0x03ffffff3f", "0x0700000000000000", "0x01").forEach { hex ->
            assertThrows(hex, MetadataDecodeException::class.java) { ScaleInput(hex.fromHex()).readCompactU32() }
        }
    }

    @Test
    fun `writing a compact outside u32 fails`() {
        assertThrows(IllegalArgumentException::class.java) { ScaleOutput().writeCompactU32(1L shl 32) }
        assertThrows(IllegalArgumentException::class.java) { ScaleOutput().writeCompactU32(-1) }
    }

    @Test
    fun `strings are length prefixed UTF-8 and invalid UTF-8 is rejected`() {
        assertEquals("0x08c3a9", ScaleOutput().writeString("é").toByteArray().toHexString(true))
        assertEquals("é", ScaleInput("0x08c3a9".fromHex()).readString())
        assertThrows(MetadataDecodeException::class.java) { ScaleInput("0x04ff".fromHex()).readString() }
        assertThrows(MetadataDecodeException::class.java) { ScaleInput("0x08c3".fromHex()).readString() }
    }

    @Test
    fun `options and booleans accept only zero and one`() {
        assertNull(ScaleInput("0x00".fromHex()).readOption { readU8() })
        assertEquals(7, ScaleInput("0x0107".fromHex()).readOption { readU8() })
        assertThrows(MetadataDecodeException::class.java) { ScaleInput("0x02".fromHex()).readOption { readU8() } }
        assertThrows(MetadataDecodeException::class.java) { ScaleInput("0x02".fromHex()).readBool() }
    }

    @Test
    fun `lengths beyond the input and trailing bytes are rejected`() {
        assertThrows(MetadataDecodeException::class.java) { ScaleInput("0x0c0102".fromHex()).readByteVec() }
        assertThrows(MetadataDecodeException::class.java) { ScaleInput("0x0001".fromHex()).apply { readU8() }.requireEnd() }
        assertThrows(MetadataDecodeException::class.java) { ScaleInput(ByteArray(0)).readU8() }
    }

    @Test
    fun `fixed width integers are little-endian`() {
        assertEquals("0x0201", ScaleOutput().writeU16(0x0102).toByteArray().toHexString(true))
        assertEquals("0x04030201", ScaleOutput().writeU32(0x01020304).toByteArray().toHexString(true))
        val input = ScaleInput("0x020104030201".fromHex())
        assertEquals(0x0102, input.readU16())
        assertEquals(0x01020304L, input.readU32())
    }

    @Test
    fun `option strings and vectors encode their presence and length`() {
        assertEquals("0x00", ScaleOutput().writeOptionString(null).toByteArray().toHexString(true))
        assertEquals("0x010461", ScaleOutput().writeOptionString("a").toByteArray().toHexString(true))
        assertEquals("0x080102", ScaleOutput().writeVec(listOf(1, 2)) { writeU8(it) }.toByteArray().toHexString(true))
        assertEquals(listOf(1, 2), ScaleInput("0x080102".fromHex()).readVec { readU8() })
    }
}
