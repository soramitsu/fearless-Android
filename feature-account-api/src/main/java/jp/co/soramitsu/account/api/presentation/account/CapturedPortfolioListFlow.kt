package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.model.TotalBalance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** A row's name, icon and money share the captured portfolio for this wallet ID. */
data class CapturedWalletPortfolio<T>(val metaId: Long, val portfolio: CapturedPortfolioDetail<T>)

/**
 * Owns one set of wallet observers for the current ordered roster. A roster change immediately
 * replaces old rows with placeholders before waiting for cancelled icon/reader cleanup.
 */
fun <T> capturedPortfolioListFlow(
    metaIds: Flow<List<Long>>,
    balances: (Long) -> Flow<TotalBalance>,
    resolveDetail: suspend (TotalBalance.Ready) -> T?
): Flow<List<CapturedWalletPortfolio<T>>> = channelFlow<List<CapturedWalletPortfolio<T>>> {
    val producer = this
    val generation = AtomicLong()
    var publisher: Job? = null
    send(emptyList())
    try {
        metaIds.map { it.toList() }.distinctUntilChanged().collect { ids ->
            require(ids.distinct().size == ids.size) { "Duplicate wallet IDs" }
            val admittedGeneration = generation.incrementAndGet()
            publisher?.cancel()
            send(ids.map { CapturedWalletPortfolio<T>(it, CapturedPortfolioDetail<T>(TotalBalance.AwaitingSelection, null)) })
            publisher?.join()
            publisher = if (ids.isEmpty()) null else launch {
                val originalJob = currentCoroutineContext()[Job]!!
                val rows = ids.map { id ->
                    val admitted = flow { emitAll(balances(id)) }.map { source ->
                        when (source) {
                            is TotalBalance.Ready -> source.takeIf { it.meta.id == id } ?: TotalBalance.Unavailable
                            is TotalBalance.Loading -> source.takeIf { it.metaId == id } ?: TotalBalance.Unavailable
                            else -> source
                        }
                    }.catch { failure ->
                        if (failure is CancellationException) throw failure
                        // Isolate a wallet read before the detail producer can fail its channel.
                        // Other wallet rows keep their admitted snapshots and observers.
                        emit(TotalBalance.Unavailable)
                    }
                    capturedPortfolioDetailFlow(admitted, resolveDetail)
                        .catch { failure ->
                            if (failure is CancellationException) {
                                if (originalJob.isActive && generation.get() == admittedGeneration) producer.cancel(failure)
                                throw failure
                            }
                            emit(CapturedPortfolioDetail<T>(TotalBalance.Unavailable, null))
                        }
                        .map { CapturedWalletPortfolio(id, it) }
                }
                combine(rows) { it.toList() }.collect { current ->
                    if (originalJob.isActive && generation.get() == admittedGeneration) send(current)
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        generation.incrementAndGet()
        publisher?.cancel()
        send(emptyList())
        publisher?.join()
        throw failure
    }
}.buffer(0).catch { failure ->
    // An independent row cancellation must revoke the roster before ending this collector.
    // Actual collector teardown remains cancellation-transparent and cannot publish new state.
    currentCoroutineContext().ensureActive()
    emit(emptyList())
    throw failure
}

