package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.model.cache.CachedAssetRead
import jp.co.soramitsu.coredb.model.cache.CachedAssetWithPriceRead
import jp.co.soramitsu.coredb.model.cache.CachedTokenPriceRead
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.expectedFiatPriceId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.atomic.AtomicLong

/** Each delivered account/currency selection gets a new reference identity, including A→B→A. */
class CurrentAccountAssetContext(val meta: MetaAccount, val fiat: FiatValuationContext)

/**
 * Catalog authority belongs to this snapshot instance. Account/fiat context alone is shared
 * across catalog revisions; asynchronous downstream work must retain and revalidate the snapshot.
 */
data class CurrentAccountAssetSnapshot(
    val context: CurrentAccountAssetContext,
    val chainsById: Map<String, Chain>,
    val assets: List<CachedAssetWithPriceRead>,
    val quotesReady: Boolean
)

sealed interface SelectedAccountAssetSnapshot {
    data object AwaitingSelection : SelectedAccountAssetSnapshot
    data class Loading(val context: CurrentAccountAssetContext) : SelectedAccountAssetSnapshot
    data class Ready(val snapshot: CurrentAccountAssetSnapshot) : SelectedAccountAssetSnapshot
}

/**
 * Asset quantities and independent currency-qualified quotes share one captured account/catalog.
 * Revocation covers delivered events. Callers must handle terminal failures and any buffering
 * they add downstream; this reader cannot fence selection events not yet delivered upstream.
 */
class CurrentAccountAssetReader(
    private val assets: AssetDao,
    private val chains: ChainsRepository,
    private val accounts: AccountRepository,
    private val prices: TokenPriceDao,
    private val selectedFiat: SelectedFiat
) {
    /** Fixed account; selection/loading states remain visible to callers. */
    fun observeSnapshots(meta: MetaAccount): Flow<SelectedAccountAssetSnapshot> = observeContexts(flowOf(meta))

    /** A fixed wallet still observes account overrides, recovery and deletion. */
    fun observeAccountSnapshots(metaId: Long): Flow<SelectedAccountAssetSnapshot> =
        observeContexts(accounts.metaAccountStateFlow(metaId))

    /** Captured historical snapshot; its currency must never be relabelled as a later selection. */
    suspend fun getSnapshot(metaId: Long): CurrentAccountAssetSnapshot {
        val fiat = FiatValuationContext(selectedFiat.get())
        val context = CurrentAccountAssetContext(accounts.getMetaAccount(metaId), fiat)
        val catalog = chains.getChainsById().toMap()
        val balances = selectBalances(context, catalog, assets.getPortfolioBalances(metaId))
        val quotes = prices.getTokenPricesByIds(quoteIds(context, catalog, balances))
        return withQuotes(context, catalog, balances, quotes, ready = true)
    }

    fun observeSelectedSnapshots(): Flow<SelectedAccountAssetSnapshot> =
        observeContexts(accounts.selectedMetaAccountStateFlow())

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeContexts(selected: Flow<MetaAccount?>): Flow<SelectedAccountAssetSnapshot> = channelFlow {
        val generation = AtomicLong()
        var publisher: Job? = null
        send(SelectedAccountAssetSnapshot.AwaitingSelection)
        combine(selected, selectedFiat.flow()) { meta, fiat -> meta to fiat }.collect { (meta, fiat) ->
            val revision = generation.incrementAndGet()
            val prior = publisher
            prior?.cancel()
            val context = meta?.let { CurrentAccountAssetContext(it, FiatValuationContext(fiat)) }
            send(context?.let { SelectedAccountAssetSnapshot.Loading(it) } ?: SelectedAccountAssetSnapshot.AwaitingSelection)
            prior?.join()
            publisher = context?.let {
                launch {
                    val owner = currentCoroutineContext().job
                    observeCapturedSnapshot(it) { snapshot ->
                        if (generation.get() == revision && owner.isActive) {
                            send(SelectedAccountAssetSnapshot.Ready(snapshot))
                        }
                    }
                }
            }
        }
    }.buffer(0)

    /**
     * Revoke old quotes before waiting for old query cleanup, retaining completed crypto quantities.
     * Publish directly from the quote Job through the public handoff: an intermediate channel
     * would transfer ownership and let an old catalog's blocked send survive cancellation.
     */
    private suspend fun observeCapturedSnapshot(
        context: CurrentAccountAssetContext,
        publish: suspend (CurrentAccountAssetSnapshot) -> Unit
    ): Unit = coroutineScope {
        val generation = AtomicLong()
        var publisher: Job? = null
        combine(chains.chainsByIdFlow(), assets.observePortfolioBalances(context.meta.id)) { catalog, rows ->
            val captured = catalog.toMap()
            captured to selectBalances(context, captured, rows)
        }.collect { (catalog, balances) ->
            val revision = generation.incrementAndGet()
            val prior = publisher
            prior?.cancel()
            publish(withQuotes(context, catalog, balances, emptyList(), ready = false))
            prior?.join()
            publisher = launch {
                val owner = currentCoroutineContext().job
                prices.observeTokenPricesByIds(quoteIds(context, catalog, balances)).collect { quotes ->
                    if (generation.get() == revision && owner.isActive) {
                        publish(withQuotes(context, catalog, balances, quotes, ready = true))
                    }
                }
            }
        }
    }

    private fun selectBalances(
        context: CurrentAccountAssetContext,
        catalog: Map<String, Chain>,
        rows: List<CachedAssetRead>
    ): List<CachedAssetWithPriceRead> =
        selectCurrentAccountAssets(context.meta, catalog, rows.map { CachedAssetWithPriceRead(it, null) })

    private fun quoteIds(
        context: CurrentAccountAssetContext,
        catalog: Map<String, Chain>,
        balances: List<CachedAssetWithPriceRead>
    ): List<String> = balances.mapNotNull { row ->
        catalog[row.asset.chainId]?.assetsById?.get(row.asset.id)
            ?.let { expectedFiatPriceId(it, context.fiat.currencyId) }
            .takeIf { context.fiat.isValid && !it.isNullOrBlank() }
    }.distinct()

    private fun withQuotes(
        context: CurrentAccountAssetContext,
        catalog: Map<String, Chain>,
        balances: List<CachedAssetWithPriceRead>,
        quotes: List<CachedTokenPriceRead>,
        ready: Boolean
    ): CurrentAccountAssetSnapshot {
        val byId = quotes.associateBy { it.priceId }
        val joined = balances.map { row ->
            val configuration = catalog.getValue(row.asset.chainId).assetsById.getValue(row.asset.id)
            val key = expectedFiatPriceId(configuration, context.fiat.currencyId)
                .takeIf { context.fiat.isValid && !it.isNullOrBlank() }
            CachedAssetWithPriceRead(row.asset, key?.let(byId::get))
        }
        return CurrentAccountAssetSnapshot(context, catalog, joined, ready)
    }
}

/** An unavailable exact balance still wins over a fallback; availability never changes ownership. */
internal fun selectCurrentAccountAssets(
    meta: MetaAccount,
    chains: Map<String, Chain>,
    rows: List<CachedAssetWithPriceRead>
): List<CachedAssetWithPriceRead> {
    val currentAccounts = chains.mapValues { (_, chain) -> meta.accountId(chain) }
    val candidates = rows.filter { row ->
        val asset = row.asset
        val chain = chains[asset.chainId]
        asset.metaId == meta.id && chain?.assetsById?.containsKey(asset.id) == true &&
            (asset.accountId.isEmpty() || asset.accountId.contentEquals(currentAccounts[asset.chainId]))
    }
    val exactKeys = candidates.asSequence().filter { it.asset.accountId.isNotEmpty() }
        .map { it.asset.chainId to it.asset.id }.toSet()
    return candidates.filter { row ->
        row.asset.accountId.isNotEmpty() || (row.asset.chainId to row.asset.id) !in exactKeys
    }
}
