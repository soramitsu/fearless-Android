package jp.co.soramitsu.walletconnect.impl.presentation.summary

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u128
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.walletconnect.impl.presentation.PolkadotSignPayload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class PolkadotSigningSummaryTest {
    private val accountId = FixedByteArray("AccountId", 32)
    private val multiAddress = DictEnum(
        "MultiAddress",
        listOf(
        DictEnum.Entry("Id", TypeReference(accountId)), DictEnum.Entry("Index", TypeReference(u128)),
        DictEnum.Entry("Raw", TypeReference(Bytes)), DictEnum.Entry("Address32", TypeReference(accountId)),
        DictEnum.Entry("Address20", TypeReference(FixedByteArray("Address20", 20)))
    )
    )
    private val modules = listOf(
        module("Accounts", 6, "send", listOf(FunctionArgument("dest", multiAddress))),
        module("Balances", 5, "transfer_keep_alive", listOf(FunctionArgument("dest", accountId), FunctionArgument("value", u128))),
        module("System", 0, "remark", listOf(FunctionArgument("remark", Bytes))),
        module("Utility", 26, "batch", listOf(FunctionArgument("calls", Vec("Calls", TypeReference(GenericCall)))))
    )
    private val runtime = RuntimeSnapshot(
        TypeRegistry(v13Preset() + ("AccountId" to TypeReference(accountId)), DynamicTypeResolver.defaultCompoundResolver()),
        RuntimeMetadata(
            BigInteger.valueOf(14),
            modules.associateBy {
            it.name
        },
            ExtrinsicMetadata(BigInteger.valueOf(4), emptyList())
        )
    )
    private val decoder = PolkadotCallDecoder(runtime) { "address:${it.toHexString()}" }

    @Test
    fun `real SCALE balances call shows runtime method destination and exact native units`() {
        val result = requireNotNull(decoder.decode(transfer()))
        assertEquals("Balances.transfer_keep_alive", result.name)
        assertEquals(PolkadotValue.Account("address:${"11".repeat(32)}"), result.arguments[0].value)
        assertEquals(PolkadotValue.NativeAmount(BigInteger.valueOf(42)), result.arguments[1].value)
        assertTrue(result.isComplete)
        assertTrue(PolkadotCallDecoder.isNativeAmount("Staking", "value"))
        assertFalse(PolkadotCallDecoder.isNativeAmount("Assets", "amount"))
    }

    @Test
    fun `MultiAddress Id is rendered as an account rather than opaque bytes`() {
        val result = requireNotNull(decoder.decode("060000".fromHex() + ByteArray(32) { 0x11 }))
        assertEquals(PolkadotValue.Account("address:${"11".repeat(32)}"), result.arguments.single().value)
    }

    @Test
    fun `trailing bytes truncated amounts and unknown runtime functions are undecoded`() {
        for (bytes in listOf(transfer() + byteArrayOf(0), transfer().dropLast(1).toByteArray(), "ff00".fromHex())) {
            assertNull(decoder.decode(bytes))
        }
    }

    @Test
    fun `SCALE batch decodes all nested calls in order`() {
        val result = requireNotNull(decoder.decode("1a0008".fromHex() + transfer() + transfer()))
        val calls = result.arguments.single().value as PolkadotValue.Calls
        assertEquals(2, calls.calls.size)
        assertEquals("Balances.transfer_keep_alive", calls.calls.first().name)
        assertTrue(result.isComplete)
    }

    @Test
    fun `nested calls beyond displayed depth preserve raw fallback`() {
        var bytes = transfer()
        repeat(5) { bytes = "1a0004".fromHex() + bytes }
        assertFalse(requireNotNull(decoder.decode(bytes)).isComplete)
    }

    @Test
    fun `remark UTF8 is readable and oversize argument remains raw`() {
        val remark = decoder.decode("0000086869".fromHex())!!
        assertEquals(PolkadotValue.Text("\"hi\""), remark.arguments.single().value)
        // compact SCALE length 600 = (600 << 2) | 1, little endian.
        val long = decoder.decode("00006109".fromHex() + ByteArray(600) { 65 })!!
        assertFalse(long.isComplete)
    }

    @Test
    fun `preview decodes exactly the method bytes of the payload and reads its era`() {
        val method = transfer().toHexString(withPrefix = true)
        val json = """{"address":"wallet","genesisHash":"0x${"aa".repeat(32)}","method":"$method","tip":"0x03","blockHash":"0x${"bb".repeat(32)}","era":"0x00","nonce":"0x02","specVersion":"0x01","transactionVersion":"0x02"}"""
        val payload = PolkadotSignPayload.parse(json)
        assertTrue(payload.isImmortal)
        assertEquals("wallet", payload.address)
        assertEquals(BigInteger.valueOf(3), payload.tip)
        assertArrayEquals(transfer(), payload.method)
        assertEquals("Balances.transfer_keep_alive", decoder.decode(payload.method)?.name)
        assertFalse(PolkadotSignPayload.parse(json.replace("\"era\":\"0x00\"", "\"era\":\"0xf500\"")).isImmortal)
    }

    private fun transfer() = "0500".fromHex() + ByteArray(32) { 0x11 } + byteArrayOf(42) + ByteArray(15)

    private fun module(
        name: String,
        index: Int,
        function: String,
        arguments: List<FunctionArgument>
    ) = Module(
        name, null, mapOf(function to MetadataFunction(function, arguments, emptyList(), index to 0)),
        emptyMap(), emptyMap(), emptyMap(), index.toBigInteger()
    )
}
