package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.common.data.network.send.PendingWalletSend
import jp.co.soramitsu.common.data.network.send.WalletSendJournal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Saves public signed bytes before submission and never replaces a possibly submitted payment. */
class NativeSendExecutor(private val journal: WalletSendJournal) {
    /** A retry polls and resubmits the retained bytes. Provider ambiguity always remains pending. */
    suspend fun execute(
        binding: NativeSendBinding,
        quoteId: String?,
        kind: PendingWalletSend.Kind,
        requireCurrentWallet: suspend () -> Unit,
        prepare: suspend () -> PendingWalletSend,
        submit: suspend (PendingWalletSend) -> Unit,
        status: suspend (PendingWalletSend) -> PendingWalletSend.State?
    ): String = journal.withWalletLock(binding.scope) {
        requireCurrentWallet()
        var prior = journal.read(binding.scope)
        val wasPending = prior?.state == PendingWalletSend.State.Pending
        if (wasPending) {
            val observed = safely { status(prior!!) }
            requireCurrentWallet()
            if (observed != null && observed != PendingWalletSend.State.Pending) {
                prior = journal.settle(binding.scope, prior!!, observed)
            }
        }
        val payment = when {
            (prior != null && prior?.quoteId == quoteId) || wasPending -> {
                val retained = requireNotNull(prior)
                if (retained.quoteId != quoteId || retained.kind != kind || retained.fingerprint != binding.fingerprint) {
                    throw NativeSendException(NativeSendException.Code.PAYMENT_PENDING, retained.transactionId)
                }
                retained
            }
            else -> {
                if (quoteId == null) throw NativeSendException(NativeSendException.Code.REVIEW_REQUIRED)
                val prepared = prepare()
                require(prepared.kind == kind && prepared.quoteId == quoteId && prepared.fingerprint == binding.fingerprint)
                currentCoroutineContext().ensureActive()
                requireCurrentWallet()
                journal.retain(binding.scope, prepared)
            }
        }
        if (payment.state == PendingWalletSend.State.Failed) {
            throw NativeSendException(NativeSendException.Code.PAYMENT_FAILED, payment.transactionId)
        }
        if (payment.state == PendingWalletSend.State.Confirmed) return@withWalletLock payment.transactionId
        currentCoroutineContext().ensureActive()
        requireCurrentWallet()
        // Once retained, any failure (including cancellation after submission) leaves the same bytes.
        safely { submit(payment) }
        val observed = safely { status(payment) }
        requireCurrentWallet()
        if (observed != null && observed != PendingWalletSend.State.Pending) {
            journal.settle(binding.scope, payment, observed)
            if (observed == PendingWalletSend.State.Failed) {
                throw NativeSendException(NativeSendException.Code.PAYMENT_FAILED, payment.transactionId)
            }
        }
        payment.transactionId
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try { block() } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) { null }
}
