package jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite

import jp.co.soramitsu.fearless_utils.common.assertThrows
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.BaseTypeTest
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.errors.EncodeDecodeException
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionTest : BaseTypeTest() {

    private val optionalBoolean = Option(
        "test",
        TypeReference(BooleanType)
    )

    private val optionalU8 = Option(
        "test",
        TypeReference(u8)
    )

    @Test
    fun `should decode optional true`() {
        val inHex = "0x02"
        val decoded = optionalBoolean.fromHex(runtime, inHex)

        assertEquals(true, decoded)
    }

    @Test
    fun `should throw for wrong optional boolean value`() {
        val inHex = "0x05"

        assertThrows<EncodeDecodeException> {
            optionalBoolean.fromHex(runtime, inHex)
        }
    }

    @Test
    fun `should decode optional false`() {
        val inHex = "0x01"
        val decoded = optionalBoolean.fromHex(runtime, inHex)

        assertEquals(false, decoded)
    }

    @Test
    fun `should decode null boolean`() {
        val inHex = "0x00"
        val decoded = optionalBoolean.fromHex(runtime, inHex)

        assertEquals(null, decoded)
    }

    @Test
    fun `should decode non-null other type`() {
        val inHex = "0x0109"
        val decoded = optionalU8.fromHex(runtime, inHex)

        assertEquals(9.toBigInteger(), decoded)
    }

    @Test
    fun `should decode null other type`() {
        val inHex = "0x00"
        val decoded = optionalU8.fromHex(runtime, inHex)

        assertEquals(null, decoded)
    }

    @Test
    fun `should encode non-boolean instance`() {
        val encoded = optionalU8.toHex(runtime, 9.toBigInteger())

        assertEquals("0x0109", encoded)
    }

    @Test
    fun `should encode null non-boolean instance`() {
        val encoded = optionalU8.toHex(runtime, null)

        assertEquals("0x00", encoded)
    }

    @Test
    fun `should encode boolean instance`() {
        val encoded = optionalBoolean.toHex(runtime, true)

        assertEquals("0x02", encoded)
    }

    @Test
    fun `should validate instance`() {
        assertTrue(optionalBoolean.isValidInstance(null))
        assertTrue(optionalBoolean.isValidInstance(true))
        assertTrue(optionalU8.isValidInstance(4.toBigInteger()))

        assertFalse(optionalU8.isValidInstance(listOf(1)))

        assertFalse(optionalBoolean.isValidInstance(1))
        assertFalse(optionalU8.isValidInstance(1))
    }
}