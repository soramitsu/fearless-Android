package jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.BaseTypeTest
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u128
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DictEnumTest : BaseTypeTest() {

    private val enumValues = listOf(
        DictEnum.Entry("A", TypeReference(u8)),
        DictEnum.Entry("B", TypeReference(BooleanType)),
        DictEnum.Entry("C", TypeReference(u128))
    )

    private val type = DictEnum(
        "test",
        enumValues,
    )

    private val enumValuesCustomIndexing = mapOf(
        0 to DictEnum.Entry("A", TypeReference(u8)),
        1 to DictEnum.Entry("B", TypeReference(u128)),
        8 to DictEnum.Entry("C", TypeReference(BooleanType)),
        128 to DictEnum.Entry("D", TypeReference(Null))
    )

    private val typeCustomIndexing = DictEnum("test", enumValuesCustomIndexing)

    @Test
    fun `should decode instance`() {
        val expectedInstance = DictEnum.Entry("B", true)
        val inHex = "0x0101"

        val decoded = type.fromHex(runtime, inHex)

        assertEquals(expectedInstance.name, decoded.name)
        assertEquals(expectedInstance.value, decoded.value)
    }

    @Test
    fun `should decode instance with custom indexing`() {
        val expectedInstance = DictEnum.Entry("C", true)
        val inHex = "0x0801"

        val decoded = typeCustomIndexing.fromHex(runtime, inHex)

        assertEquals(expectedInstance.name, decoded.name)
        assertEquals(expectedInstance.value, decoded.value)
    }

    @Test
    fun `should decode instance if index greater than Byte range`() {
        val expectedInstance = DictEnum.Entry("D", null)
        val inHex = "0x80"

        val decoded = typeCustomIndexing.fromHex(runtime, inHex)

        assertEquals(expectedInstance.name, decoded.name)
        assertEquals(expectedInstance.value, decoded.value)
    }

    @Test
    fun `should encode instance if index greater than Byte range`() {
        val instance = DictEnum.Entry("D", null)

        val encoded = typeCustomIndexing.toHex(runtime, instance)

        assertEquals("0x80", encoded)
    }

    @Test
    fun `should encode instance with custom indexing`() {
        val instance = DictEnum.Entry("C", true)

        val encoded = typeCustomIndexing.toHex(runtime, instance)

        assertEquals("0x0801", encoded)
    }

    @Test
    fun `should encode instance`() {
        val instance = DictEnum.Entry("A", 1.toBigInteger())

        val encoded = type.toHex(runtime, instance)

        assertEquals("0x0001", encoded)
    }

    @Test
    fun `decoded entries compare by value`() {
        assertEquals(type.fromHex(runtime, "0x0101"), type.fromHex(runtime, "0x0101"))
        assertEquals(DictEnum.Entry("B", true), type.fromHex(runtime, "0x0101"))
        assertNotEquals(DictEnum.Entry("B", false), type.fromHex(runtime, "0x0101"))
        assertNotEquals(DictEnum.Entry("A", true), DictEnum.Entry("B", true))
    }

    @Test
    fun `should validate instance`() {
        assertTrue(type.isValidInstance(DictEnum.Entry("B", false)))

        assertFalse(type.isValidInstance(DictEnum.Entry("F", 1)))
        assertFalse(type.isValidInstance(DictEnum.Entry("A", "Not a Bigint")))

        assertFalse(type.isValidInstance(1))
    }
}