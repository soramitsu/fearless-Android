package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.core.extrinsic.ExtrinsicSubmissionTracking
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import java.math.BigInteger

/** The attempt gate is sealed before releasing a proven-unsent record; late writers cannot revive it. */
class PoolJoinSubmissionTracking(
    private val journal: PoolJoinJournal,
    private val scope: PoolJoinJournal.Scope,
    private val amount: BigInteger,
    private val poolId: BigInteger,
    private val anchor: jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader.Anchor,
    private val create: PoolJoinJournal.CreateDetails? = null,
    private val onSubmitted: () -> Unit
) : ExtrinsicSubmissionTracking {
    private val lock = Any()
    private var closed = false
    private var admitted = false
    private var acknowledged = false
    private var retained: PoolJoinJournal.Payment? = null

    override fun guard(signedExtrinsic: String, intent: String, authority: MutationExecutionGuard): MutationExecutionGuard {
        authority.check(intent)
        retained = journal.retain(scope, amount, poolId, signedExtrinsic, intent, anchor, create)
        authority.check(intent)
        return object : MutationExecutionGuard {
            override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T =
                authority.runIfAuthorized(intentSha256, operation)

            override fun <T> runSubmissionIfAuthorized(intentSha256: String, operation: () -> T): T =
                authority.runSubmissionIfAuthorized(intentSha256) {
                    synchronized(lock) {
                        check(!closed && !admitted) { "Pool submission attempt is closed" }
                        admitted = true
                    }
                    // No storage, review, wallet or attempt monitor is held across output.
                    operation()
                }
        }
    }

    override fun acknowledged(transactionHash: String) {
        val payment = checkNotNull(retained)
        require(transactionHash == payment.transactionHash)
        synchronized(lock) { acknowledged = true }
        // Consume known success before persistence or cancellable dispatch back to presentation.
        onSubmitted()
        try { journal.acknowledge(payment) } catch (_: Exception) {
            // Retained bytes remain unresolved. A failed status update cannot authorize a new payment.
        }
    }

    override fun finished() {
        val unsent = synchronized(lock) {
            closed = true
            !admitted && !acknowledged
        }
        if (unsent) retained?.let(journal::discardUnsubmitted)
    }
}
