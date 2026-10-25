package jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics

import jp.co.soramitsu.fearless_utils.common.assertThrows
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.BaseTypeTest
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.errors.EncodeDecodeException
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.event
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import org.junit.Assert.*
import org.junit.Test

class GenericEventTest : BaseTypeTest() {

    val inHex = "0x01000103"

    val module = runtime.metadata.module("A")
    val event = module.event("A")

    val instance = GenericEvent.Instance(
        module = module,
        event = event,
        arguments = listOf(
            true,
            3.toBigInteger()
        )
    )

    @Test
    fun `should encode correct event`() {
        val encoded = GenericEvent.toHex(runtime, instance)

        assertEquals(inHex, encoded)
    }

    @Test
    fun `should decode correct call`() {
        val decoded = GenericEvent.fromHex(runtime, inHex)

        assertEquals(instance.arguments, decoded.arguments)
        assertEquals(instance.module, decoded.module)
        assertEquals(instance.event, decoded.event)
    }

    @Test
    fun `should throw for encoding instance with invalid arguments`() {
        val invalidInstance = GenericEvent.Instance(
            module = module,
            event = event,
            arguments = listOf(
                "arg1" to true,
                "arg2" to 3 // invalid param type - should be BigInteger
            )
        )

        assertThrows<EncodeDecodeException> { GenericEvent.toHex(runtime, invalidInstance) }
    }

    @Test
    fun `should throw if decoding instance with invalid index`() {
        val inHex = "0x0203"

        assertThrows<EncodeDecodeException> { GenericEvent.fromHex(runtime, inHex) }
    }

    @Test
    fun `should throw if decoding instance with invalid arguments`() {
        val inHex = "0x01000412"

        assertThrows<EncodeDecodeException> { GenericEvent.fromHex(runtime, inHex) }
    }

    @Test
    fun `should validate instance`() {
        assertTrue(GenericEvent.isValidInstance(instance))

        assertFalse(GenericEvent.isValidInstance(1))
    }
}