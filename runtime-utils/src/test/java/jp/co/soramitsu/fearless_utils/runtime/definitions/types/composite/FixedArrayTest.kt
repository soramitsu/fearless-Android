package jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.BaseTypeTest
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FixedArrayTest : BaseTypeTest() {

    private val typeInstance = listOf(
        true, false, true, true
    )

    private val type = FixedArray(
        "test",
        typeInstance.size,
        TypeReference(BooleanType)
    )

    private val inHex = "0x01000101"

    @Test
    fun `should decode instance`() {
        val decoded = type.fromHex(runtime, inHex)

        assertEquals(typeInstance, decoded)
    }

    @Test
    fun `should encode instance`() {
        val encoded = type.toHex(runtime, typeInstance)

        assertEquals(inHex, encoded)
    }

    @Test
    fun `should validate instance`() {
        assertTrue(type.isValidInstance(typeInstance))
        assertFalse(type.isValidInstance(listOf(false)))

        assertFalse(type.isValidInstance(listOf(1)))
        assertFalse(type.isValidInstance(1))
    }
}