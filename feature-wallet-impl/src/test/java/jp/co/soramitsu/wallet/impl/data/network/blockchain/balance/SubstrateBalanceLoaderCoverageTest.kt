package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.common.model.NetworkScanState
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.wallet.api.data.BalanceLoader.BalanceLoaderAction
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Loads and subscribes Substrate balances against the live Polkadot runtime snapshot of the parity
 * resources (spec 2005000), so storage keys and SCALE values are the real ones.
 */
class SubstrateBalanceLoaderCoverageTest {

    private val chainRegistry = mockk<ChainRegistry>()
    private val runtimeProvider = mockk<RuntimeProvider>()
    private val remoteStorage = mockk<RemoteStorageSource>()
    private val scanState = NetworkScanStateStore(InMemoryPreferences())

    @Test
    fun `wallets without substrate or ethereum keys load nothing and touch no chain`(): Unit = runBlocking {
        val loader = loader(chain(dot()))
        val tonOnly = WalletTestModels.metaAccount(substrate = null, ethereum = null, ton = ByteArray(32) { 3 })

        val updates = loader.loadBalance(setOf(tonOnly))

        assertTrue(updates.isEmpty())
        coVerify(exactly = 0) { chainRegistry.checkChainSyncedUp(any()) }
        assertNull(scanState.states.value[NetworkScanKey(tonOnly.id, CHAIN_ID)])
    }

    @Test
    fun `an unsynced chain is set up before the System Account balance is read`(): Unit = runBlocking {
        val chain = chain(dot())
        coEvery { chainRegistry.checkChainSyncedUp(chain) } returns false
        coEvery { chainRegistry.setupChain(chain) } returns Unit
        stubRuntime()
        val requested = stubStorage { keys -> keys.associateWith { accountInfoHex(free = 1_000, reserved = 20) } }

        val updates = loader(chain).loadBalance(setOf(ACCOUNT))

        coVerify(exactly = 1) { chainRegistry.setupChain(chain) }
        assertEquals(listOf(systemAccountKey()), requested.captured)
        val update = updates.single()
        assertEquals("dot", update.id)
        assertEquals(CHAIN_ID, update.chainId)
        assertEquals(ACCOUNT.id, update.metaId)
        assertTrue(update.accountId.contentEquals(ACCOUNT_ID))
        assertEquals(BigInteger.valueOf(1_000), update.freeInPlanks)
        assertEquals(BigInteger.valueOf(20), update.reservedInPlanks)
        val state = state()
        assertNotNull(state.lastSuccessMillis)
        assertFalse(state.isStale)
    }

    @Test
    fun `a runtime that never arrives fails the scan as runtime unavailable`() = runTest {
        val chain = chain(dot())
        coEvery { chainRegistry.checkChainSyncedUp(chain) } returns true
        coEvery { chainRegistry.awaitRuntimeProvider(CHAIN_ID) } coAnswers { awaitCancellation() }

        val updates = loader(chain).loadBalance(setOf(ACCOUNT))

        assertTrue(updates.isEmpty())
        assertEquals("Runtime unavailable", state().errorMessage)
        assertTrue(state().isStale)
        coVerify(exactly = 0) { remoteStorage.queryKeys(any(), any(), any()) }
    }

    @Test
    fun `cancellation while syncing the chain is rethrown and does not fail the scan`(): Unit = runBlocking {
        val chain = chain(dot())
        coEvery { chainRegistry.checkChainSyncedUp(chain) } throws CancellationException("screen closed")

        val error = runCatching { loader(chain).loadBalance(setOf(ACCOUNT)) }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals("screen closed", error?.message)
        assertNull(state().errorMessage)
        assertFalse(state().isStale)
    }

    @Test
    fun `a failed storage read keeps the cache and marks the scan failed`(): Unit = runBlocking {
        val chain = chain(dot())
        coEvery { chainRegistry.checkChainSyncedUp(chain) } returns true
        stubRuntime()
        coEvery { remoteStorage.queryKeys(any(), CHAIN_ID, null) } throws IllegalStateException("storage offline")

        val updates = loader(chain).loadBalance(setOf(ACCOUNT))

        assertTrue(updates.isEmpty())
        assertEquals("storage offline", state().errorMessage)
        assertTrue(state().isStale)
    }

    @Test
    fun `an asset whose key cannot be built fails the scan but other balances still load`(): Unit = runBlocking {
        val chain = chain(dot(), unsupported())
        coEvery { chainRegistry.checkChainSyncedUp(chain) } returns true
        stubRuntime()
        val requested = stubStorage { keys -> keys.associateWith { accountInfoHex(free = 7, reserved = 0) } }

        val updates = loader(chain).loadBalance(setOf(ACCOUNT))

        assertEquals(listOf(systemAccountKey()), requested.captured)
        assertEquals(listOf("dot"), updates.map(AssetBalanceUpdateItem::id))
        assertEquals(BigInteger.valueOf(7), updates.single().freeInPlanks)
        assertEquals("Unable to construct storage key for bad", state().errorMessage)
        assertTrue(state().isStale)
    }

    @Test
    fun `an undecodable balance is left out and fails the scan`(): Unit = runBlocking {
        val chain = chain(dot())
        coEvery { chainRegistry.checkChainSyncedUp(chain) } returns true
        stubRuntime()
        stubStorage { keys -> keys.associateWith { "0x0102" } }

        val updates = loader(chain).loadBalance(setOf(ACCOUNT))

        assertTrue(updates.isEmpty())
        assertEquals("Binding is incompatible", state().errorMessage)
    }

    @Test
    fun `equilibrium and unsupported token types without storage values load as zero`(): Unit = runBlocking {
        val chain = chain(equilibrium(), erc20())
        coEvery { chainRegistry.checkChainSyncedUp(chain) } returns true
        stubRuntime()
        val requested = stubStorage { emptyMap() }

        val updates = loader(chain).loadBalance(setOf(ACCOUNT))

        assertEquals(listOf(systemAccountKey(), systemAccountKey()), requested.captured)
        assertEquals(listOf("eq", "erc"), updates.map(AssetBalanceUpdateItem::id))
        updates.forEach { assertEquals(BigInteger.ZERO, it.freeInPlanks) }
        assertNull(state().errorMessage)
        assertFalse(state().isStale)
    }

    @Test
    fun `subscription emits the changed balance and records a successful scan`(): Unit = runBlocking {
        val chain = chain(dot())
        val cancellable = mockk<SocketService.Cancellable>(relaxed = true)
        val socket = subscribedSocket(cancellable) { keys ->
            listOf(change(listOf(keys.single(), accountInfoHex(free = 55, reserved = 4))))
        }
        stubConnection(socket)

        val action = loader(chain).subscribeBalance(ACCOUNT).first()

        val balance = (action as BalanceLoaderAction.UpdateBalance).balance
        assertEquals("dot", balance.id)
        assertEquals(ACCOUNT.id, balance.metaId)
        assertTrue(balance.accountId.contentEquals(ACCOUNT_ID))
        assertEquals(BigInteger.valueOf(55), balance.freeInPlanks)
        assertEquals(BigInteger.valueOf(4), balance.reservedInPlanks)
        assertNotNull(state().lastSuccessMillis)
        assertNull(state().errorMessage)
        verify { cancellable.cancel() }
    }

    @Test
    fun `undecodable subscription values fail the scan until a decodable change arrives`(): Unit = runBlocking {
        val chain = chain(dot())
        val store = mockk<NetworkScanStateStore>(relaxed = true)
        val socket = subscribedSocket(mockk(relaxed = true)) { keys ->
            listOf(
                change(listOf(UNKNOWN_KEY, accountInfoHex(free = 1, reserved = 0)), listOf(keys.single(), "0x0102")),
                change(listOf(keys.single(), accountInfoHex(free = 9, reserved = 0)))
            )
        }
        stubConnection(socket)

        val action = loader(chain, store).subscribeBalance(ACCOUNT).first()

        assertEquals(BigInteger.valueOf(9), (action as BalanceLoaderAction.UpdateBalance).balance.freeInPlanks)
        verifyOrder {
            store.scanStarted(ACCOUNT.id, CHAIN_ID, AssetDiscoveryCoverage.CatalogOnly, any())
            store.scanFailed(ACCOUNT.id, CHAIN_ID, AssetDiscoveryCoverage.CatalogOnly, "Binding is incompatible", any())
            store.scanSucceeded(ACCOUNT.id, CHAIN_ID, AssetDiscoveryCoverage.CatalogOnly, any())
        }
    }

    @Test
    fun `a connection failure ends the subscription and fails the scan`(): Unit = runBlocking {
        val chain = chain(dot())
        stubRuntime()
        coEvery { chainRegistry.awaitConnection(CHAIN_ID) } throws IllegalStateException("no connection")

        val actions = loader(chain).subscribeBalance(ACCOUNT).toList()

        assertTrue(actions.isEmpty())
        assertEquals("no connection", state().errorMessage)
        assertTrue(state().isStale)
    }

    @Test
    fun `a wallet without an account on the chain subscribes to nothing`(): Unit = runBlocking {
        val chain = chain(dot())
        val socket = mockk<SocketService>()
        stubConnection(socket)
        val noAccountId = ACCOUNT.copy(substrateAccountId = null)

        val actions = loader(chain).subscribeBalance(noAccountId).toList()

        assertTrue(actions.isEmpty())
        verify(exactly = 0) { socket.subscribe(any(), any(), any()) }
        assertNotNull(state().lastAttemptMillis)
        assertNull(state().errorMessage)
    }

    private fun loader(chain: Chain, store: NetworkScanStateStore = scanState) = SubstrateBalanceLoader(
        chain = chain,
        chainRegistry = chainRegistry,
        remoteStorageSource = remoteStorage,
        substrateSource = mockk<SubstrateRemoteSource>(),
        operationDao = mockk<OperationDao>(),
        scanStateStore = store
    )

    private fun state(): NetworkScanState = scanState.states.value.getValue(NetworkScanKey(ACCOUNT.id, CHAIN_ID))

    private fun stubRuntime() {
        coEvery { chainRegistry.awaitRuntimeProvider(CHAIN_ID) } returns runtimeProvider
        coEvery { runtimeProvider.get() } returns POLKADOT
    }

    private fun stubStorage(answer: (List<String>) -> Map<String, String?>): CapturingSlot<List<String>> {
        val keys = slot<List<String>>()
        coEvery { remoteStorage.queryKeys(capture(keys), CHAIN_ID, null) } answers { answer(keys.captured) }
        return keys
    }

    private fun stubConnection(socket: SocketService) {
        stubRuntime()
        val connection = mockk<ChainConnection>()
        every { connection.socketService } returns socket
        coEvery { chainRegistry.awaitConnection(CHAIN_ID) } returns connection
    }

    /** A socket that answers the balance subscription with [changes] built from the requested keys. */
    private fun subscribedSocket(
        cancellable: SocketService.Cancellable,
        changes: (List<String>) -> List<SubscriptionChange>
    ): SocketService {
        val socket = mockk<SocketService>()
        every { socket.subscribe(any(), any(), any()) } answers {
            val request = firstArg<RuntimeRequest>()
            val listener = secondArg<SocketService.ResponseListener<SubscriptionChange>>()
            val keys = (request.params.first() as List<*>).map { it as String }
            changes(keys).forEach(listener::onNext)
            cancellable
        }
        return socket
    }

    private fun change(vararg entries: List<String?>) = SubscriptionChange(
        jsonrpc = "2.0",
        method = "state_storage",
        params = SubscriptionChange.Params(
            result = mapOf("block" to "0x01", "changes" to entries.toList()),
            subscription = "balance-subscription"
        )
    )

    private fun systemAccountKey(): String = POLKADOT.metadata.system().storage("Account").storageKey(POLKADOT, ACCOUNT_ID)

    private fun dot() = asset("dot", ChainAssetType.Normal, currencyId = null, isUtility = true)

    private fun unsupported() = asset("bad", ChainAssetType.Unknown, currencyId = "7", isUtility = false)

    private fun equilibrium() = asset("eq", ChainAssetType.Equilibrium, currencyId = "1", isUtility = true)

    private fun erc20() = asset("erc", ChainAssetType.ERC20, currencyId = null, isUtility = false)

    private fun asset(id: String, type: ChainAssetType, currencyId: String?, isUtility: Boolean): Asset =
        WalletTestModels.asset(id = id, chainId = CHAIN_ID, type = type, currencyId = currencyId, isUtility = isUtility)

    private fun chain(vararg assets: Asset): Chain = WalletTestModels.chain(id = CHAIN_ID, assets = assets.toList())

    private companion object {
        const val CHAIN_ID = "substrate-coverage"
        const val UNKNOWN_KEY = "0xdeadbeef"
        val ACCOUNT_ID = ByteArray(32) { 2 }
        val ACCOUNT: MetaAccount = WalletTestModels.metaAccount(id = 4L, substrate = ACCOUNT_ID, ethereum = null)

        val POLKADOT: RuntimeSnapshot by lazy {
            val loader = requireNotNull(SubstrateBalanceLoaderCoverageTest::class.java.classLoader)
            val metadata = GZIPInputStream(requireNotNull(loader.getResourceAsStream("parity/polkadot_metadata_v14.hex.gz")))
                .bufferedReader().use { it.readText() }.trim()
            val types = requireNotNull(loader.getResourceAsStream("parity/polkadot_parity_types.json"))
                .bufferedReader().use { it.readText() }
            RuntimeFactory(JsonFactory.create()).constructRuntime(metadata, types, 2005000).runtime
        }

        /** System.Account on Polkadot: nonce, consumers, providers, sufficients (u32); free, reserved, frozen, flags (u128). */
        fun accountInfoHex(free: Long, reserved: Long): String =
            "0x" + le(3, 4) + le(0, 4) + le(1, 4) + le(0, 4) + le(free, 16) + le(reserved, 16) + le(0, 16) + le(0, 16)

        fun le(value: Long, size: Int): String = (0 until size).joinToString("") { index ->
            if (index < 8) "%02x".format((value ushr (8 * index)) and 0xff) else "00"
        }
    }
}
