package jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics

import jp.co.soramitsu.fearless_utils.common.assertInstance
import jp.co.soramitsu.fearless_utils.common.assertThrows
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypePresetBuilder
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.BaseTypeTest
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.errors.EncodeDecodeException
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataTest : BaseTypeTest() {

    val type = Data(preset())

    @Test
    fun `should decode none`() {
        val hex = "0x00"

        val decoded = type.fromHex(runtime, hex)

        assertNull(decoded.value)
        assertEquals("None", decoded.name)
    }

    @Test
    fun `should decode raw`() {
        val hex = "0x090102030405060708"

        val decodedEntry = type.fromHex(runtime, hex)

        assertEquals("Raw", decodedEntry.name)
        val value = decodedEntry.value

        assertInstance<ByteArray>(value)
        assertEquals("0102030405060708", value.toHexString())
    }

    @Test
    fun `should decode hasher`() {
        val hex = "0x241234567890123456789012345678901212345678901234567890123456789012"

        val decodedEntry = type.fromHex(runtime, hex)

        assertEquals("Keccak256", decodedEntry.name)
        val value = decodedEntry.value

        assertInstance<ByteArray>(value)
        assertEquals(
            "1234567890123456789012345678901212345678901234567890123456789012",
            value.toHexString()
        )
    }

    @Test
    fun `should throw on wrong index`() {
        val hex = "0x26"

        assertThrows<EncodeDecodeException> {
            type.fromHex(runtime, hex)
        }
    }

    @Test
    fun `should encode none`() {
        val encoded = type.toHex(runtime, DictEnum.Entry("None", null))

        assertEquals("0x00", encoded)
    }

    @Test
    fun `should encode raw`() {
        val encoded = type.toHex(runtime, DictEnum.Entry("Raw", "0x0102030405060708".fromHex()))

        assertEquals("0x090102030405060708", encoded)
    }

    @Test
    fun `should encode hasher`() {
        val encoded = type.toHex(runtime, DictEnum.Entry("Keccak256", "0x1234567890123456789012345678901212345678901234567890123456789012".fromHex()))

        assertEquals("0x241234567890123456789012345678901212345678901234567890123456789012", encoded)
    }

    private fun preset(): TypePresetBuilder {
        return mutableMapOf(
            "H256" to TypeReference(H256),
            "Bytes" to TypeReference(Bytes),
            "Null" to TypeReference(Null)
        )
    }
}
