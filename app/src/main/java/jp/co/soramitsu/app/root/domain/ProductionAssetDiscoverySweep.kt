package jp.co.soramitsu.app.root.domain

import android.util.Log
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletIroha
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.NetworkScanStateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

interface AssetDiscoveryService {
    fun isDue(now: Long = System.currentTimeMillis()): Boolean
    fun millisUntilDue(now: Long = System.currentTimeMillis()): Long
    fun resetForFullSweep()
    suspend fun scanNextBatch(batchSize: Int = 24): ProductionAssetDiscoverySweep.BatchResult
}

/**
 * Performs a deterministic, resumable sweep of the complete production chain catalog.
 *
 * Visibility, rank and feature activation are deliberately absent from selection. A bounded
 * batch cursor is persisted after every chain, so process death resumes after the last attempted
 * network instead of restarting at the popular-chain prefix.
 */
class ProductionAssetDiscoverySweep @Inject constructor(
    private val chainsRepository: ChainsRepository,
    private val accountRepository: AccountRepository,
    private val balanceLoaderProvider: BalanceLoader.Provider,
    private val assetDao: AssetDao,
    private val chainRegistry: ChainRegistry,
    private val networkScanStateStore: NetworkScanStateStore,
    private val preferences: Preferences
) : AssetDiscoveryService {

    data class BatchResult(
        val attemptedChainIds: List<String>,
        val hasMore: Boolean
    )

    override fun isDue(now: Long): Boolean {
        val unfinished = preferences.getString(PREF_CURSOR_CHAIN_ID)?.isNotBlank() == true
        val lastCompleted = preferences.getLong(PREF_LAST_COMPLETED_AT, 0L)
        return unfinished || lastCompleted == 0L || now - lastCompleted >= SWEEP_INTERVAL_MILLIS
    }

    override fun millisUntilDue(now: Long): Long {
        if (isDue(now)) return 0L
        val lastCompleted = preferences.getLong(PREF_LAST_COMPLETED_AT, 0L)
        return (lastCompleted + SWEEP_INTERVAL_MILLIS - now).coerceAtLeast(0L)
    }

    /**
     * Requests a fresh catalog pass after a wallet/catalog change or user refresh.
     * The scheduler is the sole caller while no batch is executing, so a completed
     * batch cannot race this reset and restore an obsolete cursor.
     */
    override fun resetForFullSweep() {
        preferences.removeField(PREF_CURSOR_CHAIN_ID)
        preferences.putLong(PREF_LAST_COMPLETED_AT, 0L)
    }

    override suspend fun scanNextBatch(batchSize: Int): BatchResult {
        require(batchSize > 0)
        val cursor = preferences.getString(PREF_CURSOR_CHAIN_ID)
        val productionChains = productionChainsAfter(
            chains = chainsRepository.getChains(),
            cursorChainId = cursor
        )

        if (productionChains.isEmpty()) {
            completeSweep()
            return BatchResult(emptyList(), hasMore = false)
        }

        preferences.putLong(PREF_LAST_ATTEMPT_AT, System.currentTimeMillis())
        val accounts = accountRepository.allMetaAccounts().filter { it.initialized }.toSet()
        val batch = productionChains.take(batchSize)

        batch.forEach { chain ->
            try {
                scanChain(chain, accounts)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Individual loaders persist their network scan error. The catalog cursor still
                // advances so one broken endpoint cannot starve every chain after it.
                runCatching { Log.d(TAG, "asset discovery failed for ${chain.id}: $error") }
            } finally {
                preferences.putString(PREF_CURSOR_CHAIN_ID, chain.id)
            }
        }

        val hasMore = productionChains.size > batch.size
        if (!hasMore) completeSweep()
        return BatchResult(batch.map(Chain::id), hasMore)
    }

    private suspend fun scanChain(
        chain: Chain,
        accounts: Set<jp.co.soramitsu.account.api.domain.model.MetaAccount>
    ) {
        if (accounts.isEmpty()) return
        val wasRegistryManaged = chainRegistry.syncedChains.value.any { it.id == chain.id }
        val requiresConnection = chain.requiresRegistryConnection()
        var startedForDiscovery = false

        try {
            if (requiresConnection && !wasRegistryManaged && !chainRegistry.checkChainSyncedUp(chain)) {
                chainRegistry.setupChain(chain)
                startedForDiscovery = true
            }

            val updates = withTimeoutOrNull(CHAIN_SCAN_TIMEOUT_MILLIS) {
                balanceLoaderProvider.invoke(chain).loadBalance(accounts)
            }
            if (updates == null) {
                accounts.forEach { account ->
                    networkScanStateStore.scanFailedPreservingCoverage(
                        walletId = account.id,
                        chainId = chain.id,
                        fallbackCoverage = chain.defaultDiscoveryCoverage(),
                        errorMessage = "Asset discovery timed out"
                    )
                }
                return
            }

            updates.forEach { update ->
                val priceId = chain.assets.firstOrNull { asset ->
                    asset.id == update.id || asset.currencyId == update.id
                }?.priceId
                assetDao.updateBalanceOrInsertPreservingPreference(update, priceId)
            }
        } finally {
            // The temporary connection belongs to this sweep only. If normal registry sync
            // concurrently claimed the chain, its synced-catalog membership transfers ownership
            // and prevents us from tearing down a user/feature-managed connection.
            val concurrentlyRegistryManaged = chainRegistry.syncedChains.value.any { it.id == chain.id }
            if (startedForDiscovery && !concurrentlyRegistryManaged) {
                chainRegistry.stopChain(chain)
            }
        }
    }

    private fun completeSweep() {
        preferences.removeField(PREF_CURSOR_CHAIN_ID)
        preferences.putLong(PREF_LAST_COMPLETED_AT, System.currentTimeMillis())
    }

    private fun Chain.requiresRegistryConnection(): Boolean {
        return nodes.isNotEmpty() && ecosystem in setOf(
            Ecosystem.Substrate,
            Ecosystem.Ethereum,
            Ecosystem.EthereumBased
        )
    }

    private fun Chain.defaultDiscoveryCoverage(): AssetDiscoveryCoverage = when {
        isUniversalWalletBitcoin() -> AssetDiscoveryCoverage.Limited
        isUniversalWalletSolana() || isUniversalWalletIroha() -> AssetDiscoveryCoverage.Complete
        ecosystem == Ecosystem.Substrate ||
            ecosystem == Ecosystem.Ethereum ||
            ecosystem == Ecosystem.EthereumBased -> AssetDiscoveryCoverage.CatalogOnly
        else -> AssetDiscoveryCoverage.Complete
    }

    internal companion object {
        const val SWEEP_INTERVAL_MILLIS = 24L * 60L * 60L * 1_000L
        const val DEFAULT_BATCH_SIZE = 24
        const val CHAIN_SCAN_TIMEOUT_MILLIS = 45_000L
        const val CONTINUATION_DELAY_MILLIS = 5_000L
        const val PREF_CURSOR_CHAIN_ID = "asset_discovery.production.cursor_chain_id.v1"
        const val PREF_LAST_ATTEMPT_AT = "asset_discovery.production.last_attempt_at.v1"
        const val PREF_LAST_COMPLETED_AT = "asset_discovery.production.last_completed_at.v1"
        const val TAG = "ProductionAssetSweep"

        fun productionChainsAfter(chains: List<Chain>, cursorChainId: String?): List<Chain> {
            return chains.asSequence()
                .filterNot(Chain::isTestNet)
                .sortedBy(Chain::id)
                .dropWhile { chain -> cursorChainId != null && chain.id <= cursorChainId }
                .toList()
        }
    }
}
