package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.model.TotalBalance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** An asynchronous detail belongs only to its exact captured portfolio instance. */
data class CapturedPortfolioDetail<T>(val source: TotalBalance, val detail: T?)

/**
 * Revokes the previous detail before waiting for its cancelled generator to finish. Ordinary input
 * failures revoke the portfolio too, then propagate unchanged after cleanup. Normal completion
 * waits for the final detail; collector cancellation closes the producer and its child together.
 * Consumers adding asynchronous work or buffering need their own snapshot admission guard.
 */
fun <T> capturedPortfolioDetailFlow(
    balances: Flow<TotalBalance>,
    resolveDetail: suspend (TotalBalance.Ready) -> T?
): Flow<CapturedPortfolioDetail<T>> = channelFlow<CapturedPortfolioDetail<T>> {
    val generation = AtomicLong()
    var publisher: Job? = null
    send(CapturedPortfolioDetail<T>(TotalBalance.AwaitingSelection, null))
    try {
        balances.collect { source ->
            val admittedGeneration = generation.incrementAndGet()
            publisher?.cancel()
            send(CapturedPortfolioDetail<T>(source, null))
            publisher?.join()
            publisher = if (source is TotalBalance.Ready) launch {
                val originalJob = currentCoroutineContext()[Job]!!
                val detail = try {
                    resolveDetail(source)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (originalJob.isActive && generation.get() == admittedGeneration && detail != null) {
                    send(CapturedPortfolioDetail<T>(source, detail))
                }
            } else null
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        // Do not leave a completed portfolio visible during non-cancellable detail cleanup.
        generation.incrementAndGet()
        publisher?.cancel()
        send(CapturedPortfolioDetail<T>(TotalBalance.Unavailable, null))
        publisher?.join()
        throw failure
    }
}.buffer(0).catch { failure ->
    if (failure is CancellationException) throw failure
    // A producer failure can cancel a rendezvous receiver before it handles the early revocation.
    // Emit from the collector here as well, so ordinary failure always clears the displayed state.
    emit(CapturedPortfolioDetail<T>(TotalBalance.Unavailable, null))
    throw failure
}
