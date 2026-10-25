package jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.BaseTypeTest
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TupleTest : BaseTypeTest() {
    private val type = Tuple(
        "test",
        listOf(
            TypeReference(BooleanType),
            TypeReference(u8),
        )
    )

    private val expectedInstance = listOf(
        true,
        9.toBigInteger()
    )

    private val expectedInHex = "0x0109"

    @Test
    fun `should decode instance`() {
        val decoded = type.fromHex(runtime, expectedInHex)

        assertEquals(expectedInstance, decoded)
    }

    @Test
    fun `should encode instance`() {
        val encoded = type.toHex(runtime, expectedInstance)

        assertEquals(expectedInHex, encoded)
    }

    @Test
    fun `should validate instance`() {
        assertTrue(type.isValidInstance(expectedInstance))

        assertFalse(type.isValidInstance(1))
        assertFalse(type.isValidInstance(listOf<Any?>(false)))
    }
}