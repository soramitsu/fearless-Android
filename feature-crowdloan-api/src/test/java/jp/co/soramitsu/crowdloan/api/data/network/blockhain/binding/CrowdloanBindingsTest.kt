package jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.alias
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.newBuilder
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericAccountId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u128
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntryModifier
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class CrowdloanBindingsTest {
    private val registry = TypeRegistry(
        v13Preset().newBuilder().apply { alias("Balance", "u128") },
        DynamicTypeResolver.defaultCompoundResolver()
    )

    private fun runtimeWithFunds(fundType: Type<*>?): RuntimeSnapshot {
        val funds = StorageEntry("Funds", StorageEntryModifier.Optional, StorageEntryType.Plain(fundType), ByteArray(0), emptyList(), "Crowdloan")
        val crowdloan = Module("Crowdloan", Storage("Crowdloan", mapOf("Funds" to funds)), null, null, emptyMap(), emptyMap(), BigInteger.valueOf(73))
        val metadata = mockk<RuntimeMetadata> { every { modules } returns mapOf("Crowdloan" to crowdloan) }
        return RuntimeSnapshot(registry, metadata)
    }

    private fun fundStruct(vararg fields: Pair<String, Type<*>>) =
        Struct("FundInfo", linkedMapOf(*fields.map { (name, type) -> name to TypeReference(type) }.toTypedArray()))

    @Suppress("UNCHECKED_CAST")
    private fun Type<*>.encode(runtime: RuntimeSnapshot, value: Any?) = (this as Type<Any?>).toHex(runtime, value)

    @Test fun contributionDecodesTheBalanceAndMemo() {
        val runtime = runtimeWithFunds(null)
        val scale = registry["(Balance, Vec<u8>)"]!!.encode(runtime, listOf(BigInteger("123456789"), "referral".encodeToByteArray()))

        val contribution = bindContribution(scale, runtime)

        assertEquals(BigInteger("123456789"), contribution.amount)
        assertEquals("referral", contribution.memo)
    }

    @Test fun currentFundLayoutUsesPeriodsAndFundIndex() {
        val type = fundStruct(
            "depositor" to GenericAccountId, "verifier" to BooleanType, "deposit" to u128, "raised" to u128,
            "end" to u32, "cap" to u128, "firstPeriod" to u32, "lastPeriod" to u32, "fundIndex" to u32
        )
        val runtime = runtimeWithFunds(type)
        val depositor = ByteArray(32) { 9 }
        val scale = type.encode(runtime, Struct.Instance(mapOf(
            "depositor" to depositor, "verifier" to true, "deposit" to BigInteger("5"), "raised" to BigInteger("1000"),
            "end" to BigInteger("900"), "cap" to BigInteger("5000"), "firstPeriod" to BigInteger("13"),
            "lastPeriod" to BigInteger("20"), "fundIndex" to BigInteger("44")
        )))

        val fund = bindFundInfo(scale, runtime, BigInteger.valueOf(2000))

        assertArrayEquals(depositor, fund.depositor)
        assertEquals(true, fund.verifier)
        assertEquals(BigInteger("5"), fund.deposit)
        assertEquals(BigInteger("1000"), fund.raised)
        assertEquals(BigInteger("900"), fund.end)
        assertEquals(BigInteger("5000"), fund.cap)
        assertEquals(BigInteger("13"), fund.firstSlot)
        assertEquals(BigInteger("20"), fund.lastSlot)
        assertEquals(BigInteger("44"), fund.fundIndex)
        assertEquals(BigInteger.valueOf(2000), fund.paraId)
        // The crowdloan's own account: "modlpy/cfund" ++ u32(paraId), zero padded to 32 bytes.
        val expectedBidder = "modlpy/cfund".encodeToByteArray() + byteArrayOf(0xD0.toByte(), 0x07, 0, 0) + ByteArray(16)
        assertArrayEquals(expectedBidder, fund.bidderAccountId)
    }

    @Test fun legacyFundLayoutUsesSlotsAndTrieIndex() {
        val type = fundStruct(
            "depositor" to GenericAccountId, "deposit" to u128, "raised" to u128, "end" to u32, "cap" to u128,
            "firstSlot" to u32, "lastSlot" to u32, "trieIndex" to u32
        )
        val runtime = runtimeWithFunds(type)
        val scale = type.encode(runtime, Struct.Instance(mapOf(
            "depositor" to ByteArray(32), "deposit" to BigInteger.ONE, "raised" to BigInteger.TEN, "end" to BigInteger("300"),
            "cap" to BigInteger("100"), "firstSlot" to BigInteger("2"), "lastSlot" to BigInteger("5"), "trieIndex" to BigInteger("7")
        )))

        val fund = bindFundInfo(scale, runtime, BigInteger.valueOf(2004))

        assertEquals(BigInteger("2"), fund.firstSlot)
        assertEquals(BigInteger("5"), fund.lastSlot)
        assertEquals(BigInteger("7"), fund.fundIndex)
        assertEquals(null, fund.verifier)
    }

    @Test fun undecodableFundIsIncompatible() {
        val type = fundStruct("depositor" to GenericAccountId, "deposit" to u128)
        val runtime = runtimeWithFunds(type)

        assertThrows(IllegalStateException::class.java) { bindFundInfo("0x01", runtime, BigInteger.ONE) }
    }
}
