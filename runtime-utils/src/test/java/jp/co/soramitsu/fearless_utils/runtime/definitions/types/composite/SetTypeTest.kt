package jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.BaseTypeTest
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetTypeTest : BaseTypeTest() {

    private val type = SetType(
        "test",
        TypeReference(u8),
        linkedMapOf(
            "A" to 1.toBigInteger(),
            "B" to 2.toBigInteger(),
            "C" to 4.toBigInteger(),
            "D" to 8.toBigInteger()
        )
    )

    @Test
    fun `should decode one flag`() {
        val inHex = "0x04"
        val decoded = type.fromHex(runtime, inHex)

        assertEquals(setOf("C"), decoded)
    }

    @Test
    fun `should decode multiple flags`() {
        val inHex = "0x0d"
        val decoded = type.fromHex(runtime, inHex)

        assertEquals(setOf("A", "C", "D"), decoded)
    }

    @Test
    fun `should encode one flag`() {
        val instance = setOf("C")
        val encoded = type.toHex(runtime, instance)

        assertEquals("0x04", encoded)
    }

    @Test
    fun `should encode multiple flags`() {
        val instance = setOf("A", "C", "D")
        val encoded = type.toHex(runtime, instance)

        assertEquals("0x0d", encoded)
    }

    @Test
    fun `should validate instance`() {
        assertTrue(type.isValidInstance(setOf<String>()))
        assertTrue(type.isValidInstance(setOf("A")))

        assertFalse(type.isValidInstance(listOf(1)))
        assertFalse(type.isValidInstance(1))

        assertFalse(type.isValidInstance(setOf("F")))
    }
}