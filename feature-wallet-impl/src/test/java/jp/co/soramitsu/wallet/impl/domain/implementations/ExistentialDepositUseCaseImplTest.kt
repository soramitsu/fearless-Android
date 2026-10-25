package jp.co.soramitsu.wallet.impl.domain.implementations

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.exception.RpcException
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcError
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUnavailableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AL-10: a failed existential deposit lookup is a failure, never "the minimum is zero".
 *
 * Runtimes are the read-only metadata snapshots of the AL-9 parity test; chains and assets come from the
 * bundled registry. The Asset Hub storage values were read from polkadot-asset-hub-rpc.polkadot.io at
 * finalized block #21682641 (0xf4a19a88...2d29, statemint 2005000).
 */
class ExistentialDepositUseCaseImplTest {

    @Test
    fun `registry minimum is used without asking the network`() = runBlocking {
        val karuraAusd = registryAsset(KARURA, "ausd")

        val useCase = ExistentialDepositUseCaseImpl(mockk(), mockk(), mockk())

        assertEquals(BigInteger("10000000000"), useCase(karuraAusd).getOrThrow())
    }

    @Test
    fun `Asset Hub USDT minimum is read from the asset details`() = runBlocking {
        val usdt = registryAsset(ASSET_HUB, "usdt")
        val storage = FakeStorage(runtime("asset-hub-polkadot"), mapOf(USDT_ASSET_DETAILS_KEY to USDT_ASSET_DETAILS))

        val deposit = useCase(ASSET_HUB, "asset-hub-polkadot", storage = storage)(usdt)

        assertEquals(BigInteger("10000"), deposit.getOrThrow())
        assertEquals(listOf(USDT_ASSET_DETAILS_KEY), storage.requested)
    }

    @Test
    fun `the Assets Account prefix 4_2_0 read has no value and is no longer asked`() = runBlocking {
        val usdt = registryAsset(ASSET_HUB, "usdt")
        // What 4.2.0 sent: Assets.Account with the asset id only. The node answered null and 4.2.0 used zero.
        val storage = FakeStorage(runtime("asset-hub-polkadot"), mapOf(USDT_ASSET_DETAILS_KEY to USDT_ASSET_DETAILS))

        useCase(ASSET_HUB, "asset-hub-polkadot", storage = storage)(usdt)

        assertTrue(ASSETS_ACCOUNT_PREFIX_KEY_4_2_0 !in storage.requested)
    }

    @Test
    fun `asset missing on chain is unknown, not zero`() = runBlocking {
        val usdt = registryAsset(ASSET_HUB, "usdt")

        val deposit = useCase(ASSET_HUB, "asset-hub-polkadot", storage = FakeStorage(runtime("asset-hub-polkadot"), emptyMap()))(usdt)

        assertUnknown(usdt, deposit)
    }

    @Test
    fun `native minimum is the Balances constant of the runtime`() = runBlocking {
        val dot = registryAsset(ASSET_HUB, "dot")
        val xor = registryAsset(SORA, "xor")

        assertEquals(balancesExistentialDeposit(runtime("asset-hub-polkadot")), useCase(ASSET_HUB, "asset-hub-polkadot")(dot).getOrThrow())
        // Polkadot Asset Hub 2005000 keeps 0.01 DOT.
        assertEquals(BigInteger("100000000"), useCase(ASSET_HUB, "asset-hub-polkadot")(dot).getOrThrow())
        assertEquals(balancesExistentialDeposit(runtime("sora")), useCase(SORA, "sora")(xor).getOrThrow())
    }

    @Test
    fun `Interlay has no Balances pallet, so its minimum is unknown, not zero`() = runBlocking {
        val intr = registryAsset(INTERLAY, "intr")
        val ibtc = registryAsset(INTERLAY, "ibtc")

        assertUnknown(intr, useCase(INTERLAY, "interlay")(intr))
        assertUnknown(ibtc, useCase(INTERLAY, "interlay")(ibtc))
    }

    @Test
    fun `Bifrost token2 minimum comes from the orml tokens rpc with the 4_2_0 argument`() = runBlocking {
        val dot = registryAsset(BIFROST_POLKADOT, "dot")
        val socket = FakeSocket(result = "0x5f5e100")

        val deposit = useCase(BIFROST_POLKADOT, "bifrost-polkadot", rpcCalls = socket.rpcCalls(BIFROST_POLKADOT))(dot)

        assertEquals(BigInteger("100000000"), deposit.getOrThrow())
        assertEquals("tokens_queryExistentialDeposit", socket.requests.single().method)
        assertEquals("""[{"token2":0}]""", Gson().toJson(socket.requests.single().params))
    }

    @Test
    fun `a node without the orml tokens rpc leaves the minimum unknown, not zero`() = runBlocking {
        val dot = registryAsset(BIFROST_POLKADOT, "dot")
        // What eu.bifrost-polkadot-rpc.liebi.com and acala-rpc-0.aca-api.network answered on 2026-10-11.
        val socket = FakeSocket(error = RpcError(-32601, "Method not found"))

        val deposit = useCase(BIFROST_POLKADOT, "bifrost-polkadot", rpcCalls = socket.rpcCalls(BIFROST_POLKADOT))(dot)

        assertUnknown(dot, deposit)
        assertTrue(deposit.exceptionOrNull()?.cause is RpcException)
    }

    @Test
    fun `substrate runtime that is not loaded yet is unknown, not zero`() = runBlocking {
        val dot = registryAsset(ASSET_HUB, "dot")
        val chainRegistry = mockk<ChainRegistry> {
            coEvery { getRuntimeOrNull(ASSET_HUB) } returns null
            coEvery { getChain(ASSET_HUB) } returns registryChain(ASSET_HUB)
        }

        assertUnknown(dot, ExistentialDepositUseCaseImpl(chainRegistry, mockk(), mockk())(dot))
    }

    @Test
    fun `asset types without a known source are unknown, not zero`() = runBlocking {
        val hdxDot = registryAsset(HYDRADX, "dot").copy(existentialDeposit = null)

        assertUnknown(hdxDot, useCase(HYDRADX, "hydradx")(hdxDot))
    }

    @Test
    fun `EVM assets have no minimum`() = runBlocking {
        val chainRegistry = mockk<ChainRegistry> {
            coEvery { getRuntimeOrNull(ETHEREUM) } returns null
            coEvery { getChain(ETHEREUM) } returns registryChain(ETHEREUM)
        }
        val useCase = ExistentialDepositUseCaseImpl(chainRegistry, mockk(), mockk())

        assertEquals(BigInteger.ZERO, useCase(registryAsset(ETHEREUM, "eth")).getOrThrow())
        assertEquals(BigInteger.ZERO, useCase(registryAsset(ETHEREUM, "usdt")).getOrThrow())
    }

    @Test
    fun `TON assets have no minimum`() = runBlocking {
        val ton = registryChain(TON).assets.single()
        val chainRegistry = mockk<ChainRegistry> {
            coEvery { getRuntimeOrNull(TON) } returns null
            coEvery { getChain(TON) } returns registryChain(TON)
        }

        assertEquals(BigInteger.ZERO, ExistentialDepositUseCaseImpl(chainRegistry, mockk(), mockk())(ton).getOrThrow())
    }

    @Test
    fun `an Equilibrium registry minimum is not trusted over the runtime`() = runBlocking {
        // Equilibrium keeps its minimum in EqBalances; the registry value is ignored, so without a runtime it is unknown.
        val eq = registryChain(EQUILIBRIUM).assets.first { it.existentialDeposit != null }
        val chainRegistry = mockk<ChainRegistry> {
            coEvery { getRuntimeOrNull(EQUILIBRIUM) } returns null
            coEvery { getChain(EQUILIBRIUM) } returns registryChain(EQUILIBRIUM)
        }

        assertUnknown(eq, ExistentialDepositUseCaseImpl(chainRegistry, mockk(), mockk())(eq))
    }

    @Test
    fun `malformed registry minimum falls back to the network`() = runBlocking {
        val dot = registryAsset(ASSET_HUB, "dot").copy(existentialDeposit = "0.1")

        assertEquals(BigInteger("100000000"), useCase(ASSET_HUB, "asset-hub-polkadot")(dot).getOrThrow())
    }

    @Test
    fun `runtime lookup cancellation propagates without becoming unavailable`() = runBlocking {
        val dot = registryAsset(ASSET_HUB, "dot").copy(existentialDeposit = null)
        val registry = mockk<ChainRegistry> {
            coEvery { getRuntimeOrNull(ASSET_HUB) } throws kotlinx.coroutines.CancellationException("local runtime cancelled")
        }
        val outcome = runCatching { ExistentialDepositUseCaseImpl(registry, mockk(), mockk())(dot) }
        assertTrue("Cancellation must escape the use case", outcome.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test
    fun `tokens RPC cancellation propagates without becoming unavailable`() = runBlocking {
        val dot = registryAsset(BIFROST_POLKADOT, "dot").copy(existentialDeposit = null)
        val socket = FakeSocket(failure = kotlinx.coroutines.CancellationException("local RPC cancelled"))
        val outcome = runCatching { useCase(BIFROST_POLKADOT, "bifrost-polkadot", rpcCalls = socket.rpcCalls(BIFROST_POLKADOT))(dot) }
        assertEquals(1, socket.requests.size)
        assertTrue("Cancellation must escape the use case", outcome.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    private fun assertUnknown(asset: Asset, deposit: Result<BigInteger>) {
        val error = deposit.exceptionOrNull()
        assertTrue("${asset.symbol}: expected an unknown minimum, got $deposit", error is ExistentialDepositUnavailableException)
        assertEquals(asset, (error as ExistentialDepositUnavailableException).chainAsset)
    }

    private fun useCase(
        chainId: String,
        runtimeName: String,
        storage: StorageDataSource = mockk(),
        rpcCalls: RpcCalls = mockk()
    ): ExistentialDepositUseCaseImpl {
        val runtime = runtime(runtimeName)
        val chainRegistry = mockk<ChainRegistry> {
            coEvery { getRuntimeOrNull(chainId) } returns runtime
            coEvery { getChain(chainId) } returns registryChain(chainId)
        }
        return ExistentialDepositUseCaseImpl(chainRegistry, rpcCalls, storage)
    }

    private fun balancesExistentialDeposit(runtime: RuntimeSnapshot): BigInteger {
        val bytes = requireNotNull(runtime.metadata.modules.getValue("Balances").constants["ExistentialDeposit"]).value
        return BigInteger(1, bytes.reversedArray())
    }

    private class FakeSocket(private val result: Any? = null, private val error: RpcError? = null, private val failure: Throwable? = null) {
        val requests = mutableListOf<RuntimeRequest>()

        private val socket = mockk<SocketService> {
            every { jsonMapper } returns Gson()
            every { executeRequest(any(), any(), any()) } answers {
                val request = firstArg<RuntimeRequest>()
                requests += request
                failure?.let { throw it }
                thirdArg<SocketService.ResponseListener<RpcResponse>>().onNext(RpcResponse("2.0", result, request.id, error))
                mockk(relaxed = true)
            }
        }

        fun rpcCalls(chainId: String): RpcCalls {
            val connection = mockk<ChainConnection> { every { socketService } returns socket }
            val chainRegistry = mockk<IChainRegistry> { every { getConnection(chainId) } returns connection }
            return RpcCalls(chainRegistry)
        }
    }

    private class FakeStorage(private val runtime: RuntimeSnapshot, private val values: Map<String, String>) : StorageDataSource {
        val requested = mutableListOf<String?>()

        override suspend fun <T> query(chainId: String, keyBuilder: (RuntimeSnapshot) -> String?, at: String?, binding: (String?, RuntimeSnapshot) -> T): T {
            val key = keyBuilder(runtime)
            requested += key
            return binding(values[key], runtime)
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
        ): Map<K, T> = error("not used")

        override suspend fun <T> queryChildState(
            chainId: String,
            storageKeyBuilder: (RuntimeSnapshot) -> String,
            childKeyBuilder: suspend java.io.OutputStream.(RuntimeSnapshot) -> Unit,
            binder: (String?, RuntimeSnapshot) -> T
        ): T = error("not used")
    }

    private companion object {
        const val ASSET_HUB = "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f"
        const val SORA = "7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
        const val INTERLAY = "bf88efe70e9e0e916416e8bed61f2b45717f517d7f3523e33c7b001e5ffcbc72"
        const val BIFROST_POLKADOT = "262e1b2ad728475fd6fe88e62d34c200abe6fd693931ddad144059b1eb884e5b"
        const val HYDRADX = "afdc188f45c71dacbaa0b62e16a91f726c7b8699a9748cdf715459de6b7f366d"
        const val KARURA = "baf5aabe40646d11f0ee8abbdc64f4a4b7674925cba08e4a05ff9ebed6e2126b"
        const val ETHEREUM = "1"
        const val TON = "-239"
        const val EQUILIBRIUM = "89d3ec46d2fb43ef5a9713833373d5ea666b092fa8fd68fbc34596036571b907"

        // Assets.Asset(1984): twox128("Assets") ++ twox128("Asset") ++ blake2_128_concat(1984u32).
        const val USDT_ASSET_DETAILS_KEY =
            "0x682a59d51ab9e48a8c8cc418ff9708d2d34371a193a751eea5883e9553457b2ea319d0e87221ca1ee751c1529f201522c0070000"

        // owner, issuer, admin, freezer, supply, deposit, min_balance = 10000 (0.01 USDT), is_sufficient, accounts,
        // sufficients, approvals, status.
        const val USDT_ASSET_DETAILS = "0x" +
            "d8e5d105e214d8c57e5819fb6d820883f44a617a78a7a6237cb9627ec73a26d2" +
            "d8e5d105e214d8c57e5819fb6d820883f44a617a78a7a6237cb9627ec73a26d2" +
            "d8e5d105e214d8c57e5819fb6d820883f44a617a78a7a6237cb9627ec73a26d2" +
            "d8e5d105e214d8c57e5819fb6d820883f44a617a78a7a6237cb9627ec73a26d2" +
            "ea262978f04600000000000000000000" +
            "0010a5d4e80000000000000000000000" +
            "10270000000000000000000000000000" +
            "01" + "ab360000" + "43360000" + "18000000" + "00"

        // Assets.Account(1984, _) prefix, the key 4.2.0 asked; the same node answered null at that block.
        const val ASSETS_ACCOUNT_PREFIX_KEY_4_2_0 =
            "0x682a59d51ab9e48a8c8cc418ff9708d2b99d880ec681799c0cf30e8886371da9a319d0e87221ca1ee751c1529f201522c0070000"

        val registry: List<Chain> by lazy {
            val file = listOf(File("../runtime/src/main/assets/local_chains.json"), File("runtime/src/main/assets/local_chains.json"))
                .first(File::exists)
            val type = object : TypeToken<List<ChainRemote>>() {}.type
            Gson().fromJson<List<ChainRemote>>(file.readText(), type).map(ChainRemote::toChain)
        }

        val snapshots = Json.parseToJsonElement(resource("/prod-parity/13-asset-currency-chains.json")).jsonObject
            .getValue("chains").jsonArray.map { it.jsonObject }
            .associateBy { it.getValue("chain").jsonPrimitive.content }

        val runtimes = mutableMapOf<String, RuntimeSnapshot>()

        fun registryChain(chainId: String): Chain = registry.single { it.id == chainId }

        fun registryAsset(chainId: String, symbol: String): Asset = registryChain(chainId).assets.single { it.symbol == symbol }

        fun runtime(name: String): RuntimeSnapshot = runtimes.getOrPut(name) {
            val runtimeRef = snapshots.getValue(name).getValue("runtime").jsonObject
            val file = runtimeRef.getValue("file").jsonPrimitive.content
            val body = GZIPInputStream(requireNotNull(ExistentialDepositUseCaseImplTest::class.java.getResourceAsStream("/prod-parity/$file"))).use { it.readBytes() }
            assertEquals("runtime snapshot", runtimeRef.getValue("sha256").jsonPrimitive.content, body.sha256())
            val snapshot = Json.parseToJsonElement(body.decodeToString()).jsonObject
            RuntimeFactory(JsonFactory.create()).constructRuntime(
                metadataRaw = snapshot.getValue("metadata").jsonPrimitive.content,
                ownTypesRaw = snapshot.getValue("types").jsonPrimitive.content,
                runtimeVersion = snapshot.getValue("specVersion").jsonPrimitive.int
            ).runtime
        }

        fun resource(path: String): String = requireNotNull(ExistentialDepositUseCaseImplTest::class.java.getResource(path)).readText()

        fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("", "0x") { "%02x".format(it) }
    }
}
