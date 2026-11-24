package jp.co.soramitsu.crowdloan.impl.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
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
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u128
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntryModifier
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger

class CrowdloanRepositoryImplTest {
    private val remoteStorage = mockk<StorageDataSource>()
    private val chainRegistry = mockk<ChainRegistry>()
    private val repository = CrowdloanRepositoryImpl(remoteStorage, chainRegistry)

    // v14 runtimes name types by lookup index; index 0 is the 32-byte account id.
    private val registry = TypeRegistry(
        v13Preset().newBuilder().apply {
            alias("Balance", "u128")
            alias("0", "GenericAccountId")
        },
        DynamicTypeResolver.defaultCompoundResolver()
    )
    private val fundType = Struct(
        "FundInfo",
        linkedMapOf(
            "depositor" to TypeReference(GenericAccountId), "deposit" to TypeReference(u128), "raised" to TypeReference(u128),
            "end" to TypeReference(u32), "cap" to TypeReference(u128), "firstPeriod" to TypeReference(u32),
            "lastPeriod" to TypeReference(u32), "fundIndex" to TypeReference(u32)
        )
    )
    private val crowdloanRuntime = runtime(
        mapOf(
            "Crowdloan" to Module(
                "Crowdloan",
                Storage("Crowdloan", mapOf("Funds" to StorageEntry("Funds", StorageEntryModifier.Optional, StorageEntryType.Plain(fundType), ByteArray(0), emptyList(), "Crowdloan"))),
                null, null, emptyMap(), emptyMap(), BigInteger.valueOf(73)
            )
        )
    )

    private fun runtime(modules: Map<String, Module>) =
        RuntimeSnapshot(registry, mockk<RuntimeMetadata> { every { this@mockk.modules } returns modules })

    @Suppress("UNCHECKED_CAST")
    private fun Type<*>.encode(value: Any?) = (this as Type<Any?>).toHex(crowdloanRuntime, value)

    @Test fun crowdloansAreAvailableOnlyWithThePallet() = runTest {
        coEvery { chainRegistry.getRuntime("polkadot") } returns crowdloanRuntime
        coEvery { chainRegistry.getRuntime("westend") } returns runtime(emptyMap())

        assertTrue(repository.isCrowdloansAvailable("polkadot"))
        assertFalse(repository.isCrowdloansAvailable("westend"))
    }

    @Test fun fundsAreReadByPrefixAndKeyedByParachain() = runTest {
        val prefix = slot<(RuntimeSnapshot) -> String?>()
        val keyExtractor = slot<(String) -> BigInteger>()
        val binding = slot<(String?, RuntimeSnapshot, BigInteger) -> FundInfo>()
        val stored = mapOf<BigInteger, FundInfo>()
        coEvery { remoteStorage.queryByPrefix("polkadot", capture(prefix), capture(keyExtractor), capture(binding)) } returns stored

        assertSame(stored, repository.allFundInfos("polkadot"))

        val key = prefix.captured(crowdloanRuntime)!!
        assertTrue(key.startsWith("0x"))
        assertEquals(2 + 64, key.length)
        assertEquals(BigInteger.valueOf(2000), keyExtractor.captured(key + "d0070000"))
        val scale = fundType.encode(
            Struct.Instance(
                mapOf(
                    "depositor" to ByteArray(32) { 2 }, "deposit" to BigInteger.ONE, "raised" to BigInteger.TEN, "end" to BigInteger("99"),
                    "cap" to BigInteger("1000"), "firstPeriod" to BigInteger.ONE, "lastPeriod" to BigInteger.TWO, "fundIndex" to BigInteger("31")
                )
            )
        )
        val fund = binding.captured(scale, crowdloanRuntime, BigInteger.valueOf(2000))
        assertEquals(BigInteger("31"), fund.fundIndex)
        assertEquals(BigInteger.valueOf(2000), fund.paraId)
    }

    @Test fun contributionIsReadFromTheFundsChildTrie() = runTest {
        val accountId = ByteArray(32) { 5 }
        val storageKey = slot<(RuntimeSnapshot) -> String>()
        val childKey = slot<suspend OutputStream.(RuntimeSnapshot) -> Unit>()
        val binder = slot<(String?, RuntimeSnapshot) -> Contribution?>()
        val contribution = Contribution(BigInteger.TEN, "")
        coEvery { remoteStorage.queryChildState("kusama", capture(storageKey), capture(childKey), capture(binder)) } returns contribution

        assertSame(contribution, repository.getContribution("kusama", accountId, BigInteger.valueOf(2004), BigInteger.valueOf(9)))

        assertEquals(accountId.toHexString(withPrefix = true), storageKey.captured(crowdloanRuntime))
        val written = ByteArrayOutputStream().also { childKey.captured.invoke(it, crowdloanRuntime) }.toByteArray()
        assertArrayEquals(("crowdloan".encodeToByteArray() + byteArrayOf(9, 0, 0, 0)).blake2b256(), written)
        assertNull(binder.captured(null, crowdloanRuntime))
        val scale = registry["(Balance, Vec<u8>)"]!!.encode(listOf(BigInteger("250"), ByteArray(0)))
        assertEquals(BigInteger("250"), binder.captured(scale, crowdloanRuntime)?.amount)
    }
}
