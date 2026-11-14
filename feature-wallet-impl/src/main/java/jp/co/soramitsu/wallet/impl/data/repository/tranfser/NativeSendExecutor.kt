package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.common.data.network.send.PendingWalletSend
import jp.co.soramitsu.common.data.network.send.WalletSendJournal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Saves public signed bytes before submission and never replaces a possibly submitted payment. */
class NativeSendExecutor(
    private val journal: WalletSendJournal,
    /**
     * Told before a payment's record is removed because it can never land, to close its history row; the record
     * stays if this fails. `verified` is false when the payment is too old for an expiry to show that nothing left
     * the wallet.
     */
    private val onReleased: suspend (payment: PendingWalletSend, verified: Boolean) -> Unit = { _, _ -> },
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    /**
     * A retry polls and resubmits the retained bytes. Provider ambiguity always remains pending: a payment is
     * released only by chain evidence or by a proof that it can never land ([NativeSendNeverLandedException]).
     * A release always ends the call, so the user learns about it before anything new is signed or sent.
     */
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
        prior?.takeIf { it.state == PendingWalletSend.State.Pending }?.let { pending ->
            val observed = try {
                safely { status(pending) }
            } catch (proof: NativeSendNeverLandedException) {
                val verified = proof.corroborated && pending.expiryVerifiable(nowMillis())
                release(binding, pending, requireCurrentWallet, verified)
                // These bytes can never be sent again. A different payment may proceed once the user confirms it again.
                throw NativeSendException(
                    when {
                        pending.quoteId == quoteId && verified -> NativeSendException.Code.PAYMENT_EXPIRED
                        pending.quoteId == quoteId -> NativeSendException.Code.PAYMENT_UNVERIFIED
                        verified -> NativeSendException.Code.PRIOR_PAYMENT_EXPIRED
                        else -> NativeSendException.Code.PRIOR_PAYMENT_UNVERIFIED
                    },
                    pending.transactionId
                )
            }
            requireCurrentWallet()
            if (observed != null && observed != PendingWalletSend.State.Pending) {
                prior = journal.settle(binding.scope, pending, observed)
            }
        }
        val wasPending = prior?.state == PendingWalletSend.State.Pending
        var firstSubmission = false
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
                journal.retain(binding.scope, prepared).also { firstSubmission = it === prepared }
            }
        }
        if (payment.state == PendingWalletSend.State.Failed) {
            throw NativeSendException(NativeSendException.Code.PAYMENT_FAILED, payment.transactionId)
        }
        if (payment.state == PendingWalletSend.State.Confirmed) return@withWalletLock payment.transactionId
        currentCoroutineContext().ensureActive()
        requireCurrentWallet()
        // Once retained, any failure (including cancellation after submission) leaves the same bytes.
        try {
            safely { submit(payment) }
        } catch (proof: NativeSendNeverLandedException) {
            // Only the first submission can prove that no node has the payment. A refused retry proves nothing
            // about earlier attempts, which stay pending until the chain decides.
            if (firstSubmission && proof.reason != NativeSendNeverLandedException.Reason.EXPIRED) {
                release(binding, payment, requireCurrentWallet, verified = true)
                throw NativeSendException(
                    if (proof.reason == NativeSendNeverLandedException.Reason.NOT_SENT) NativeSendException.Code.PAYMENT_NOT_SENT
                    else NativeSendException.Code.PAYMENT_REFUSED,
                    payment.transactionId
                )
            }
        }
        val observed = try {
            safely { status(payment) }
        } catch (proof: NativeSendNeverLandedException) {
            val verified = proof.corroborated && payment.expiryVerifiable(nowMillis())
            release(binding, payment, requireCurrentWallet, verified)
            throw NativeSendException(
                if (verified) NativeSendException.Code.PAYMENT_EXPIRED else NativeSendException.Code.PAYMENT_UNVERIFIED,
                payment.transactionId
            )
        }
        requireCurrentWallet()
        if (observed != null && observed != PendingWalletSend.State.Pending) {
            journal.settle(binding.scope, payment, observed)
            if (observed == PendingWalletSend.State.Failed) {
                throw NativeSendException(NativeSendException.Code.PAYMENT_FAILED, payment.transactionId)
            }
        }
        payment.transactionId
    }

    /**
     * Removes a payment proven never to land, only for the wallet that still owns it. Its history row is closed
     * first and the record is removed only after that worked: if either step stops, the next check finds the
     * record and releases it again.
     */
    private suspend fun release(
        binding: NativeSendBinding,
        payment: PendingWalletSend,
        requireCurrentWallet: suspend () -> Unit,
        verified: Boolean
    ) {
        requireCurrentWallet()
        onReleased(payment, verified)
        requireCurrentWallet()
        journal.discard(binding.scope, payment)
    }

    /** Provider failures stay ambiguous; cancellation and a proof that a payment can never land pass through. */
    private suspend fun <T> safely(block: suspend () -> T): T? = try { block() } catch (error: CancellationException) {
        throw error
    } catch (error: NativeSendNeverLandedException) {
        throw error
    } catch (_: Exception) { null }

    companion object {
        /**
         * How long after signing an expiry with no trace still shows that nothing left the wallet. Nodes keep only
         * part of the ledger, so an empty lookup for an older payment may only mean that its history is gone.
         */
        const val VERIFIABLE_FOR_MILLIS: Long = 6 * 60 * 60 * 1000L
    }
}

/** Whether an expiry seen at [nowMillis] still shows that this payment never left the wallet. */
fun PendingWalletSend.expiryVerifiable(nowMillis: Long): Boolean =
    nowMillis - createdAtMillis in 0..NativeSendExecutor.VERIFIABLE_FOR_MILLIS
