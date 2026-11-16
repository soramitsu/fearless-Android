package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountData
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.RuntimeFactory
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
import jp.co.soramitsu.wallet.api.data.cache.bindEquilibriumAccountData
import jp.co.soramitsu.wallet.impl.WalletTestModels
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Equilibrium keeps every token of an account in one System.Account entry. The storage keys come from the
 * live Polkadot runtime snapshot; the Equilibrium account decoding is stubbed because no Equilibrium
 * runtime snapshot is bundled with the tests.
 */
class EquilibriumBalanceLoaderCoverageTest {

    private val chainRegistry = mockk<ChainRegistry>()
    private val runtimeProvider = mockk<RuntimeProvider>()
    private val remoteStorage = mockk<RemoteStorageSource>()
    private val chain: Chain = WalletTestModels.chain(
        id = CHAIN_ID,
        assets = listOf(
            WalletTestModels.asset(id = "eq", chainId = CHAIN_ID, currencyId = EQ_ID, type = ChainAssetType.Equilibrium),
            WalletTestModels.asset(
                id = "eqd",
                chainId = CHAIN_ID,
                currencyId = EQD_ID,
                type = ChainAssetType.Equilibrium,
                isUtility = false
            )
        )
    )

    @Before
    fun mockStatics() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        mockkStatic(BINDINGS)
        every { bindEquilibriumAccountData(EQ_HEX, any()) } returns EqAccountInfo(
            nonce = BigInteger.ONE,
            data = EqAccountData(lock = BigInteger.ZERO, balances = mapOf(BigInteger(EQ_ID) to BigInteger.valueOf(5_000)))
        )
    }

    @After
    fun restoreStatics() {
        unmockkStatic(BINDINGS)
        unmockkStatic(Log::class)
    }

    @Test
    fun `an unsynced chain is set up and every token is read from one account entry`(): Unit = runBlocking {
        coEvery { chainRegistry.checkChainSyncedUp(chain) } returns false
        coEvery { chainRegistry.setupChain(chain) } returns Unit
        stubRuntime()
        val keys = slot<List<String>>()
        coEvery { remoteStorage.queryKeys(capture(keys), CHAIN_ID, null) } answers {
            keys.captured.associateWith { EQ_HEX } + (UNKNOWN_KEY to "0x00")
        }
        val withoutSubstrate = WalletTestModels.metaAccount(id = 9L, substrate = null)

        val updates = loader().loadBalance(setOf(ACCOUNT, withoutSubstrate))

        coVerify(exactly = 1) { chainRegistry.setupChain(chain) }
        assertEquals(listOf(accountKey()), keys.captured)
        assertEquals(listOf("eq" to BigInteger.valueOf(5_000), "eqd" to BigInteger.ZERO), updates.map { it.id to it.freeInPlanks })
        updates.forEach {
            assertEquals(ACCOUNT.id, it.metaId)
            assertEquals(CHAIN_ID, it.chainId)
            assertTrue(it.accountId.contentEquals(ACCOUNT_ID))
        }
    }

    @Test
    fun `a wallet without a substrate account has no balance subscription`(): Unit = runBlocking {
        val actions = loader().subscribeBalance(WalletTestModels.metaAccount(substrate = null)).toList()

        assertTrue(actions.isEmpty())
        coVerify(exactly = 0) { chainRegistry.awaitRuntimeProvider(any()) }
    }

    @Test
    fun `subscription skips failures and ambiguous changes and emits every token of the account`(): Unit = runBlocking {
        stubRuntime()
        val socket = mockk<SocketService>()
        val requestedKeys = mutableListOf<String>()
        every { socket.subscribe(any(), any(), any()) } answers {
            val keys = (firstArg<RuntimeRequest>().params.first() as List<*>).map { it as String }
            requestedKeys += keys
            val listener = secondArg<SocketService.ResponseListener<SubscriptionChange>>()
            listener.onError(IllegalStateException("socket hiccup"))
            listener.onNext(change(listOf(keys.single(), EQ_HEX), listOf(UNKNOWN_KEY, EQ_HEX)))
            listener.onNext(change(listOf(keys.single(), EQ_HEX)))
            mockk<SocketService.Cancellable>(relaxed = true)
        }
        val connection = mockk<ChainConnection>()
        every { connection.socketService } returns socket
        coEvery { chainRegistry.awaitConnection(CHAIN_ID) } returns connection

        val balances = loader().subscribeBalance(ACCOUNT).take(2).toList()
            .map { (it as BalanceLoaderAction.UpdateBalance).balance }

        assertEquals(listOf(accountKey()), requestedKeys)
        assertEquals(listOf("eq" to BigInteger.valueOf(5_000), "eqd" to BigInteger.ZERO), balances.map { it.id to it.freeInPlanks })
        balances.forEach { assertTrue(it.accountId.contentEquals(ACCOUNT_ID)) }
        verify { Log.d(any(), match { it.contains("socket hiccup") }) }
    }

    @Test
    fun `subscription ends without updates when the connection cannot be reached`(): Unit = runBlocking {
        stubRuntime()
        coEvery { chainRegistry.awaitConnection(CHAIN_ID) } throws IllegalStateException("no socket")

        val actions = loader().subscribeBalance(ACCOUNT).toList()

        assertTrue(actions.isEmpty())
        verify { Log.d(any(), match { it.contains("no socket") }) }
    }

    private fun loader() = EquilibriumBalanceLoader(chain, chainRegistry, remoteStorage)

    private fun stubRuntime() {
        coEvery { chainRegistry.awaitRuntimeProvider(CHAIN_ID) } returns runtimeProvider
        coEvery { runtimeProvider.get() } returns POLKADOT
    }

    private fun accountKey(): String = POLKADOT.metadata.system().storage("Account").storageKey(POLKADOT, ACCOUNT_ID)

    private fun change(vararg entries: List<String?>) = SubscriptionChange(
        jsonrpc = "2.0",
        method = "state_storage",
        params = SubscriptionChange.Params(
            result = mapOf("block" to "0x02", "changes" to entries.toList()),
            subscription = "eq-subscription"
        )
    )

    private companion object {
        const val BINDINGS = "jp.co.soramitsu.wallet.api.data.cache.AssetCacheExtKt"
        const val CHAIN_ID = "equilibrium-coverage"
        const val EQ_ID = "25969"
        const val EQD_ID = "6648164"
        const val EQ_HEX = "0x0e0e"
        const val UNKNOWN_KEY = "0xdeadbeef"
        val ACCOUNT_ID = ByteArray(32) { 5 }
        val ACCOUNT: MetaAccount = WalletTestModels.metaAccount(id = 3L, substrate = ACCOUNT_ID, ethereum = null)

        val POLKADOT: RuntimeSnapshot by lazy {
            val loader = requireNotNull(EquilibriumBalanceLoaderCoverageTest::class.java.classLoader)
            val metadata = GZIPInputStream(requireNotNull(loader.getResourceAsStream("parity/polkadot_metadata_v14.hex.gz")))
                .bufferedReader().use { it.readText() }.trim()
            val types = requireNotNull(loader.getResourceAsStream("parity/polkadot_parity_types.json"))
                .bufferedReader().use { it.readText() }
            RuntimeFactory(JsonFactory.create()).constructRuntime(metadata, types, 2005000).runtime
        }
    }
}
