package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

/** One admitted balance/network result. Copy actions use this address without another lookup. */
data class CapturedPortfolioHeader(
    val source: TotalBalance,
    val selectedChainId: ChainId?,
    val address: String?
)

/**
 * Revoke the prior address before waiting for resolver cancellation cleanup. Every delivered
 * balance/network pair owns a generation, including A→B→A with the same Ready instance.
 * The resolver receives only the captured account and chain; missing chains never fall back to
 * global state. Address-only failures leave the completed balance available with no address.
 *
 * Send directly from the original publisher with a rendezvous handoff. Consumers adding buffering
 * or asynchronous work need their own admission guard and must
 * not animate an obsolete address through revocation. Events not delivered upstream are outside
 * this flow's authority. Ready returned by a one-shot read remains a historical captured result.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun capturedPortfolioHeaderFlow(
    balances: Flow<TotalBalance>,
    selectedChainIds: Flow<ChainId?>,
    resolveAddress: suspend (MetaAccount, Chain) -> String? = { meta, chain ->
        withContext(Dispatchers.Default) { meta.address(chain) }
    }
): Flow<CapturedPortfolioHeader> = channelFlow {
    val generation = AtomicLong()
    var publisher: Job? = null
    send(CapturedPortfolioHeader(TotalBalance.AwaitingSelection, null, null))
    try {
        combine(balances, selectedChainIds) { source, chainId -> source to chainId }
            .collect { (source, chainId) ->
                val revision = generation.incrementAndGet()
                val previous = publisher
                previous?.cancel()
                send(CapturedPortfolioHeader(source, chainId, null))
                previous?.join()
                val ready = source as? TotalBalance.Ready
                val chain = chainId?.let { ready?.chainsById?.get(it) }
                publisher = if (ready == null || chain == null) null else launch {
                    val owner = currentCoroutineContext().job
                    val resolved = try {
                        resolveAddress(ready.meta, chain)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    if (generation.get() == revision && owner.isActive) {
                        send(CapturedPortfolioHeader(ready, chainId, resolved))
                    }
                }
            }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        // Revoke while this producer is still active, before child cleanup can delay failure.
        generation.incrementAndGet()
        publisher?.cancel()
        send(CapturedPortfolioHeader(TotalBalance.Unavailable, null, null))
        publisher?.join()
        throw failure
    }

}.buffer(0).catch { failure ->
    if (failure is CancellationException) throw failure
    // A producer failure can cancel a rendezvous receiver before it handles the early revocation.
    // Emit from the collector here as well, so ordinary failure always clears the displayed state.
    emit(CapturedPortfolioHeader(TotalBalance.Unavailable, null, null))
    throw failure
}
