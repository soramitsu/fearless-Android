package jp.co.soramitsu.wallet.impl.data.network.blockchain

import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.io.OutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.common.data.network.runtime.binding.AssetsAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountData
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.wallet.api.data.cache.bindAssetsAccountData
import jp.co.soramitsu.wallet.api.data.cache.bindEquilibriumAccountData
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.model.CBDCTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.SubstrateTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Balance reads and transfer calls of [WssSubstrateSource]. Storage keys are built on the bundled live runtime
 * snapshots (Polkadot relay and Polkadot Asset Hub); Equilibrium and Assets account values are stubbed binders
 * because their exact SCALE layout is chain specific.
 */
class WssSubstrateSourceCoverageTest {

    private val extrinsicService = mockk<ExtrinsicService>()
    private val chain: Chain = WalletTestModels.chain(id = CHAIN_ID)

    @Before
    fun stubBinders() {
        mockkStatic(BINDINGS)
        every { bindEquilibriumAccountData(EQ_HEX, any()) } returns EqAccountInfo(
            nonce = BigInteger.ONE,
            data = EqAccountData(
                lock = BigInteger.ZERO,
                balances = mapOf(BigInteger(EQ_ID) to BigInteger.valueOf(5_000), BigInteger.ONE to BigInteger.TEN)
            )
        )
        every { bindAssetsAccountData(ASSETS_HEX, any()) } returns AssetsAccountInfo(BigInteger.valueOf(777), "Liquid")
    }

    @After
    fun restoreBinders() {
        unmockkStatic(BINDINGS)
    }

    @Test
    fun `equilibrium free and total balances are the asset entry of the System account`(): Unit = runBlocking {
        val storage = FakeStorage(POLKADOT, EQ_HEX)
        val asset = asset("eq", ChainAssetType.Equilibrium, currencyId = EQ_ID)

        val source = source(storage)

        assertEquals(BigInteger.valueOf(5_000), source.getAccountFreeBalance(asset, ACCOUNT_ID))
        assertEquals(BigInteger.valueOf(5_000), source.getTotalBalance(asset, ACCOUNT_ID))
        val systemKey = POLKADOT.metadata.system().storage("Account").storageKey(POLKADOT, ACCOUNT_ID)
        assertEquals(listOf(systemKey, systemKey), storage.requested)
    }

    @Test
    fun `assets pallet free and total balances are read from Assets Account`(): Unit = runBlocking {
        val storage = FakeStorage(ASSET_HUB, ASSETS_HEX)
        val usdt = asset("usdt", ChainAssetType.Assets, currencyId = "1984")

        val source = source(storage)

        assertEquals(BigInteger.valueOf(777), source.getAccountFreeBalance(usdt, ACCOUNT_ID))
        assertEquals(BigInteger.valueOf(777), source.getTotalBalance(usdt, ACCOUNT_ID))
        val assetsKey = ASSET_HUB.metadata.module("Assets").storage("Account")
            .storageKey(ASSET_HUB, BigInteger("1984"), ACCOUNT_ID)
        assertEquals(listOf(assetsKey, assetsKey), storage.requested)
    }

    @Test
    fun `token types without substrate storage report zero without a read`(): Unit = runBlocking {
        val storage = FakeStorage(POLKADOT, EQ_HEX)
        val erc20 = asset("erc", ChainAssetType.ERC20, currencyId = null)

        val source = source(storage)

        assertEquals(BigInteger.ZERO, source.getAccountFreeBalance(erc20, ACCOUNT_ID))
        assertEquals(BigInteger.ZERO, source.getTotalBalance(erc20, ACCOUNT_ID))
        assertTrue(storage.requested.isEmpty())
    }

    @Test
    fun `equilibrium rates are keyed by the u64 asset id at the end of the storage key`(): Unit = runBlocking {
        val rates = source(FakeStorage(POLKADOT, scale = null)).getEquilibriumAssetRates(asset("eq", ChainAssetType.Equilibrium, EQ_ID))

        assertEquals(mapOf(BigInteger.TEN to null), rates)
    }

    @Test
    fun `controller account is the raw Staking Bonded value`(): Unit = runBlocking {
        val storage = FakeStorage(POLKADOT, "0x" + "07".repeat(32))

        val controller = source(storage).getControllerAccount(CHAIN_ID, ACCOUNT_ID)

        assertArrayEquals(ByteArray(32) { 7 }, controller)
        assertEquals(
            listOf(POLKADOT.metadata.staking().storage("Bonded").storageKey(POLKADOT, ACCOUNT_ID)),
            storage.requested
        )
    }

    @Test
    fun `stash account is read from the Staking Ledger and missing or broken ledgers give none`(): Unit = runBlocking {
        val ledger = "0x" + "09".repeat(32) + "00".repeat(8)

        assertArrayEquals(ByteArray(32) { 9 }, source(FakeStorage(POLKADOT, ledger)).getStashAccount(CHAIN_ID, ACCOUNT_ID))
        assertNull(source(FakeStorage(POLKADOT, scale = null)).getStashAccount(CHAIN_ID, ACCOUNT_ID))
        assertNull(source(FakeStorage(POLKADOT, "0x01")).getStashAccount(CHAIN_ID, ACCOUNT_ID))
    }

    @Test
    fun `equilibrium transfer submits EqBalances transfer with the reviewed tip and extra calls`(): Unit = runBlocking {
        val asset = asset("eq", ChainAssetType.Equilibrium, currencyId = EQ_ID)
        val transfer = transfer(asset, SubstrateTransferParams(tipInPlanks = BigInteger.TEN, appId = BigInteger.ONE))
        val formExtrinsic = stubSubmission()
        val additionalBuilders = mutableListOf<ExtrinsicBuilder>()

        val hash = source().performTransfer(SENDER_ID, chain, transfer, additional = { additionalBuilders += this }, batchAll = true)

        assertEquals("0xsubmitted", hash)
        coVerify { extrinsicService.submitExtrinsic(chain, SENDER_ID, true, BigInteger.TEN, BigInteger.ONE, any()) }
        val built = buildCall(formExtrinsic.captured, runtime(allowDeath = false, address = null))
        assertEquals("EqBalances" to "transfer", built.name)
        assertEquals(BigInteger(EQ_ID), built.arguments["asset"])
        assertArrayEquals(RECIPIENT_ID, built.arguments["to"] as ByteArray)
        assertEquals(transfer.amountInPlanks, built.arguments["value"])
        assertEquals(listOf(built.builder), additionalBuilders)
    }

    @Test
    fun `bokolo cash is sent with a xorless transfer carrying the fee limit and comment`(): Unit = runBlocking {
        val cbdc = asset("cbdc", ChainAssetType.SoraAsset, currencyId = bokoloCashTokenId)
        val params = CBDCTransferParams(comment = "invoice 7", maxAmountInPlanks = BigInteger.valueOf(77), tipInPlanks = null, appId = null)
        val transfer = transfer(cbdc, params).copy(estimateFee = BigDecimal("0.5"))
        val formExtrinsic = stubSubmission()

        source().performTransfer(SENDER_ID, chain, transfer, additional = null, batchAll = false)

        val built = buildCall(formExtrinsic.captured, runtime(allowDeath = false, address = null))
        assertEquals("LiquidityProxy" to "xorless_transfer", built.name)
        assertEquals(BigInteger.ZERO, built.arguments["dex_id"])
        assertTrue(built.arguments["asset_id"] is Struct.Instance)
        assertArrayEquals(RECIPIENT_ID, built.arguments["receiver"] as ByteArray)
        assertEquals(transfer.amountInPlanks, built.arguments["amount"])
        assertEquals(BigInteger.valueOf(5_000_000_000), built.arguments["desired_xor_amount"])
        assertEquals(BigInteger.valueOf(77), built.arguments["max_amount_in"])
        assertEquals(emptyList<Any>(), built.arguments["selected_source_types"])
        assertEquals(DictEnum.Entry("Disabled", null), built.arguments["filter_mode"])
        assertEquals("invoice 7", (built.arguments["additional_data"] as ByteArray).decodeToString())
    }

    @Test
    fun `runtimes without transfer_allow_death send Balances transfer to a raw account id`(): Unit = runBlocking {
        val formExtrinsic = stubSubmission()
        val transfer = transfer(asset("dot", ChainAssetType.Normal, currencyId = null), SubstrateTransferParams(null, null))

        source().performTransfer(SENDER_ID, chain, transfer, additional = null, batchAll = false)

        val built = buildCall(formExtrinsic.captured, runtime(allowDeath = false, address = FixedByteArray("AccountId", 32)))
        assertEquals("Balances" to "transfer", built.name)
        assertArrayEquals(RECIPIENT_ID, built.arguments["dest"] as ByteArray)
        assertEquals(transfer.amountInPlanks, built.arguments["value"])
    }

    @Test
    fun `runtimes without transfer_allow_death address a multi address recipient by id`(): Unit = runBlocking {
        val formExtrinsic = stubSubmission()
        val transfer = transfer(asset("dot", ChainAssetType.Normal, currencyId = null), SubstrateTransferParams(null, null))

        source().performTransfer(SENDER_ID, chain, transfer, additional = null, batchAll = false)

        val built = buildCall(formExtrinsic.captured, runtime(allowDeath = false, address = null))
        assertEquals("Balances" to "transfer", built.name)
        val dest = built.arguments["dest"] as DictEnum.Entry<*>
        assertEquals("Id", dest.name)
        assertArrayEquals(RECIPIENT_ID, dest.value as ByteArray)
    }

    @Test
    fun `a transfer without substrate parameters is refused before signing`(): Unit = runBlocking {
        val transfer = transfer(asset("dot", ChainAssetType.Normal, currencyId = null), params = null)

        val error = runCatching { source().performTransfer(SENDER_ID, chain, transfer, null, false) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        coVerify(exactly = 0) { extrinsicService.submitExtrinsic(any(), any(), any(), any(), any(), any()) }
    }

    private class BuiltCall(val name: Pair<String, String>, val arguments: Map<String, Any?>, val builder: ExtrinsicBuilder)

    private suspend fun buildCall(formExtrinsic: suspend ExtrinsicBuilder.() -> Unit, runtime: RuntimeSnapshot): BuiltCall {
        val builder = mockk<ExtrinsicBuilder>()
        val module = slot<String>()
        val call = slot<String>()
        val arguments = slot<Map<String, Any?>>()
        every { builder.runtime } returns runtime
        every { builder.call(capture(module), capture(call), capture(arguments)) } returns builder

        formExtrinsic.invoke(builder)

        return BuiltCall(module.captured to call.captured, arguments.captured, builder)
    }

    private fun stubSubmission(): CapturingSlot<suspend ExtrinsicBuilder.() -> Unit> {
        val formExtrinsic = slot<suspend ExtrinsicBuilder.() -> Unit>()
        coEvery {
            extrinsicService.submitExtrinsic(any(), any(), any(), any(), any(), capture(formExtrinsic))
        } returns Result.success("0xsubmitted")
        return formExtrinsic
    }

    private fun runtime(allowDeath: Boolean, address: FixedByteArray?): RuntimeSnapshot {
        val types: Map<String, TypeReference> = if (address == null) emptyMap() else mapOf("Address" to TypeReference(address))
        val registry = TypeRegistry(types = types, dynamicTypeResolver = DynamicTypeResolver())
        val calls = if (allowDeath) arrayOf("transfer_allow_death" to 0) else arrayOf("transfer" to 0)
        return RuntimeSnapshot(registry, metadata(module("Balances", 5, *calls)))
    }

    private fun source(storage: StorageDataSource = mockk()) = WssSubstrateSource(mockk<RpcCalls>(), storage, extrinsicService)

    private fun transfer(asset: Asset, params: SubstrateTransferParams?) = Transfer(
        sender = chain.addressOf(SENDER_ID),
        recipient = chain.addressOf(RECIPIENT_ID),
        amount = BigDecimal("2"),
        chainAsset = asset,
        additionalParams = params
    )

    private fun asset(id: String, type: ChainAssetType, currencyId: String?) =
        WalletTestModels.asset(id = id, chainId = CHAIN_ID, type = type, currencyId = currencyId, isUtility = false)

    /** Answers every read with [scale] decoded by the caller's binder, recording the keys it was asked for. */
    private class FakeStorage(private val runtime: RuntimeSnapshot, private val scale: String?) : StorageDataSource {
        val requested = mutableListOf<String?>()

        override suspend fun <T> query(
            chainId: String,
            keyBuilder: (RuntimeSnapshot) -> String?,
            at: String?,
            binding: (String?, RuntimeSnapshot) -> T
        ): T {
            requested += keyBuilder(runtime)
            return binding(scale, runtime)
        }

        override suspend fun <K, T> queryKeys(
            chainId: String,
            keysBuilder: (RuntimeSnapshot) -> Map<String, K>,
            at: String?,
            binding: (String?, RuntimeSnapshot) -> T
        ): Map<K, T> = error("not used")

        override fun <T> observe(chainId: String, keyBuilder: (RuntimeSnapshot) -> String?, binder: (String?, RuntimeSnapshot) -> T): Flow<T> =
            error("not used")

        override suspend fun <K, T> queryByPrefix(
            chainId: String,
            prefixKeyBuilder: (RuntimeSnapshot) -> String?,
            keyExtractor: (String) -> K,
            binding: (String?, RuntimeSnapshot, K) -> T
        ): Map<K, T> {
            val key = keyExtractor(RATE_KEY)
            return mapOf(key to binding(scale, runtime, key))
        }

        override suspend fun <T> queryChildState(
            chainId: String,
            storageKeyBuilder: (RuntimeSnapshot) -> String,
            childKeyBuilder: suspend OutputStream.(RuntimeSnapshot) -> Unit,
            binder: (String?, RuntimeSnapshot) -> T
        ): T = error("not used")
    }

    private companion object {
        const val BINDINGS = "jp.co.soramitsu.wallet.api.data.cache.AssetCacheExtKt"
        const val CHAIN_ID = "wss-coverage"
        const val EQ_ID = "25969"
        const val EQ_HEX = "0x0e0e"
        const val ASSETS_HEX = "0x0a0a"
        val RATE_KEY = "0x" + "ab".repeat(32) + "0a00000000000000"
        val ACCOUNT_ID = ByteArray(32) { 2 }
        val SENDER_ID = ByteArray(32) { 3 }
        val RECIPIENT_ID = ByteArray(32) { 4 }

        val POLKADOT: RuntimeSnapshot by lazy {
            val metadata = GZIPInputStream(resource("parity/polkadot_metadata_v14.hex.gz")).bufferedReader().use { it.readText() }.trim()
            val types = resource("parity/polkadot_parity_types.json").bufferedReader().use { it.readText() }
            RuntimeFactory(JsonFactory.create()).constructRuntime(metadata, types, 2005000).runtime
        }

        val ASSET_HUB: RuntimeSnapshot by lazy {
            val body = GZIPInputStream(resource("prod-parity/runtime/asset-hub-polkadot.json.gz")).use { it.readBytes() }
            val snapshot = Json.parseToJsonElement(body.decodeToString()).jsonObject
            RuntimeFactory(JsonFactory.create()).constructRuntime(
                metadataRaw = snapshot.getValue("metadata").jsonPrimitive.content,
                ownTypesRaw = snapshot.getValue("types").jsonPrimitive.content,
                runtimeVersion = snapshot.getValue("specVersion").jsonPrimitive.int
            ).runtime
        }

        fun resource(path: String) =
            requireNotNull(requireNotNull(WssSubstrateSourceCoverageTest::class.java.classLoader).getResourceAsStream(path)) { path }

        fun module(name: String, index: Int, vararg calls: Pair<String, Int>) = Module(
            name,
            null,
            calls.associate { (call, callIndex) ->
                call to MetadataFunction(call, listOf(FunctionArgument("value", u8)), emptyList(), index to callIndex)
            },
            emptyMap(),
            emptyMap(),
            emptyMap(),
            index.toBigInteger()
        )

        fun metadata(vararg modules: Module) = RuntimeMetadata(
            BigInteger.valueOf(14),
            modules.associateBy { it.name },
            ExtrinsicMetadata(BigInteger.valueOf(4), emptyList())
        )
    }
}
