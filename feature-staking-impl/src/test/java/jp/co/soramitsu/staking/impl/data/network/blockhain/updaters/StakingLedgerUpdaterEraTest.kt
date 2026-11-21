package jp.co.soramitsu.staking.impl.data.network.blockhain.updaters

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.coredb.dao.AccountStakingDao
import jp.co.soramitsu.coredb.model.cache.FreshAssetCacheUpdate
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.repository.AssetHubMetadataContractTest
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.core.models.Asset as CoreAsset

/**
 * The asset's redeemable and unbonding amounts depend on the ledger and on the active era. The updater classifies the
 * ledger with the era that ActiveEraUpdater caches, so a ledger that arrives while the cache still holds an earlier
 * era is classified again when the current era is cached, without another ledger event.
 */
class StakingLedgerUpdaterEraTest {
    private val runtime = AssetHubMetadataContractTest.runtimes.getValue("polkadot")
    private val stash = ByteArray(32) { 5 }
    private val chain = mock<Chain> {
        on { id } doReturn AssetHubStaking.POLKADOT
        on { ecosystem } doReturn Ecosystem.Substrate
    }
    private val dot = mock<CoreAsset> {
        on { id } doReturn "dot"
        on { chainId } doReturn AssetHubStaking.POLKADOT
        on { staking } doReturn CoreAsset.StakingType.RELAYCHAIN
    }
    private val account = mock<MetaAccount> {
        on { id } doReturn 3L
        on { chainAccounts } doReturn emptyMap()
        on { substrateAccountId } doReturn stash
    }
    private val updates = CopyOnWriteArrayList<FreshAssetCacheUpdate.Staking>()
    private val assetCache = mock<AssetCache> {
        onBlocking { updateAsset(any(), any(), any(), any()) } doAnswer {
            updates += it.getArgument<FreshAssetCacheUpdate.Staking>(3)
            Unit
        }
    }
    private val socket = mock<SocketService>()
    private val builder = mock<SubscriptionBuilder> { on { socketService } doReturn socket }

    private fun ledger(): String {
        val type = runtime.metadata.staking().storage("Ledger").returnType()
        @Suppress("UNCHECKED_CAST")
        return (type as Type<Any>).toHex(
            runtime,
            Struct.Instance(
                mapOf(
                    "stash" to stash, "total" to 1000.toBigInteger(), "active" to 900.toBigInteger(),
                    "unlocking" to listOf(Struct.Instance(mapOf("value" to 100.toBigInteger(), "era" to 10.toBigInteger())))
                )
            )
        )
    }

    private fun updater(eras: Flow<BigInteger>, ledger: String?): StakingLedgerUpdater {
        val key = runtime.metadata.staking().storage("Ledger").storageKey(runtime, stash)
        whenever(socket.subscribe(any(), any(), any())).thenAnswer {
            val change = SubscriptionChange(
                "2.0",
                "state_storage",
                SubscriptionChange.Params(mapOf("block" to "0x01", "changes" to listOf(listOf(key, ledger))), "ledger")
            )
            it.getArgument<SocketService.ResponseListener<SubscriptionChange>>(1).onNext(change)
            mock<SocketService.Cancellable>()
        }
        val repository = mock<StakingRelayChainScenarioRepository> { on { observeActiveEraIndex(any()) } doReturn eras }
        val shared = mock<StakingSharedState> {
            on { assetWithChain } doReturn flowOf(SingleAssetSharedState.AssetWithChain(chain, dot))
        }
        val registry = mock<ChainRegistry> { onBlocking { getRuntime(any()) } doReturn runtime }
        val scope = mock<AccountUpdateScope> { on { invalidationFlow() } doReturn flowOf(account) }
        return StakingLedgerUpdater(repository, shared, registry, mock<AccountStakingDao>(), mock<StorageCache>(), assetCache, scope)
    }

    private fun update(redeemable: Int, unbonding: Int, bonded: Int) =
        FreshAssetCacheUpdate.Staking(redeemable.toBigInteger(), unbonding.toBigInteger(), bonded.toBigInteger())

    private suspend fun awaitUpdate(expected: FreshAssetCacheUpdate.Staking) = withTimeout(10_000) {
        while (updates.lastOrNull() != expected) delay(5)
    }

    @Test fun `a ledger read with a stale era is classified again when the current era is cached`() = runBlocking<Unit> {
        val eras = MutableStateFlow(9.toBigInteger())
        val updater = updater(eras, ledger())
        val job = launch(Dispatchers.Default) { updater.listenForUpdates(builder).collect() }
        try {
            awaitUpdate(update(redeemable = 0, unbonding = 100, bonded = 900))
            // The era advances; the ledger does not change.
            eras.value = 10.toBigInteger()
            awaitUpdate(update(redeemable = 100, unbonding = 0, bonded = 900))
            verify(socket, times(1)).subscribe(any(), any(), any())
        } finally {
            job.cancel()
        }
    }

    @Test fun `an account without a ledger is cleared without waiting for the era`() = runBlocking<Unit> {
        val updater = updater(MutableSharedFlow(), ledger = null)
        val job = launch(Dispatchers.Default) { updater.listenForUpdates(builder).collect() }
        try {
            awaitUpdate(update(redeemable = 0, unbonding = 0, bonded = 0))
            assertEquals(1, updates.size)
        } finally {
            job.cancel()
        }
    }
}
