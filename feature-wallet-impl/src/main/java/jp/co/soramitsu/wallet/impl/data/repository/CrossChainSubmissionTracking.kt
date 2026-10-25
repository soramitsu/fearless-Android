package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.core.extrinsic.ExtrinsicSubmissionTracking
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.core.extrinsic.mortality.ExtrinsicValidity
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader

/**
 * Retains the signed transfer before any byte reaches the network and admits a single output. The record stays
 * after a send, a node acknowledgement or an ambiguous failure; it is dropped here only when the sealed gate proves
 * that nothing was handed to the network.
 */
class CrossChainSubmissionTracking(
    private val journal: CrossChainSubmissionJournal,
    private val scope: CrossChainSubmissionJournal.Scope,
    private val details: CrossChainSubmissionJournal.Details,
    private val anchor: FinalizedExtrinsicReader.Anchor
) : ExtrinsicSubmissionTracking {
    private val lock = Any()
    private var closed = false
    private var admitted = false
    private var acknowledged = false
    private var validity: ExtrinsicValidity? = null
    private var retained: CrossChainSubmissionJournal.Submission? = null

    /** The record of this attempt once its bytes are retained. */
    val submission: CrossChainSubmissionJournal.Submission?
        get() = synchronized(lock) { retained }

    override fun validity(window: ExtrinsicValidity) {
        synchronized(lock) { validity = window }
    }

    override fun guard(signedExtrinsic: String, intent: String, authority: MutationExecutionGuard): MutationExecutionGuard {
        authority.check(intent)
        val record = journal.retain(scope, details, signedExtrinsic, intent, anchor, synchronized(lock) { validity })
        synchronized(lock) { retained = record }
        authority.check(intent)
        return object : MutationExecutionGuard {
            override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T =
                authority.runIfAuthorized(intentSha256, operation)

            override fun <T> runSubmissionIfAuthorized(intentSha256: String, operation: () -> T): T =
                authority.runSubmissionIfAuthorized(intentSha256) {
                    synchronized(lock) {
                        check(!closed && !admitted) { "Cross-chain submission attempt is closed" }
                        admitted = true
                    }
                    operation()
                }
        }
    }

    override fun acknowledged(transactionHash: String) {
        val record = checkNotNull(submission)
        require(transactionHash == record.transactionHash)
        synchronized(lock) { acknowledged = true }
        try {
            journal.acknowledge(record)
        } catch (_: Exception) {
            // The retained bytes stay unsettled; a failed status update cannot allow another transfer.
        }
    }

    override fun finished() {
        val unsent = synchronized(lock) {
            closed = true
            !admitted && !acknowledged
        }
        if (unsent) submission?.let(journal::discardUnsubmitted)
    }
}
