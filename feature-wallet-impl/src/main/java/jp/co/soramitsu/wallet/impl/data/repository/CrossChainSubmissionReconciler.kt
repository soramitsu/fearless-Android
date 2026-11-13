package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * Settles an unsettled cross-chain transfer from finalized blocks of its origin network, under the wallet's
 * configured RPC trust model. It only reads: it never signs or sends.
 *
 * - The exact signed bytes in a finalized block after the anchor mean the transfer was included. Inclusion says
 *   nothing about dispatch success or delivery at the destination.
 * - A mortal transaction can only be included from its birth block until its death block. Once that whole window is
 *   finalized and none of its blocks after the anchor holds the bytes, the transfer can never be included: it was
 *   not executed.
 * - Anything else leaves the record unsettled; a miss alone never proves that a transfer did not happen. A record
 *   without a known validity window can only be settled by finding it.
 */
class CrossChainSubmissionReconciler(
    private val journal: CrossChainSubmissionJournal,
    private val reader: FinalizedExtrinsicReader
) {
    // Next block to read per attempt, kept only while the whole window is finalized so that it can only continue a
    // search that started at the window's last block. Lost on restart, which just restarts the search.
    private val progress = ConcurrentHashMap<String, BigInteger>()

    suspend fun check(scope: CrossChainSubmissionJournal.Scope, attemptId: String): Outcome = journal.withScope(scope) {
        val submission = journal.read(scope) ?: return@withScope Outcome.Settled
        check(submission.attemptId == attemptId) { "The unsettled cross-chain transfer changed" }
        val head = reader.anchor(scope.chainId)
        val lastValidBlock = submission.validity?.deathBlock?.minus(BigInteger.ONE)
        val windowFinalized = lastValidBlock != null && head.number >= lastValidBlock
        var before = if (windowFinalized) progress[attemptId] ?: lastValidBlock else null
        repeat(MAX_PAGES) {
            val search = reader.search(scope.chainId, submission.signedExtrinsic, before, PAGE_SIZE, submission.anchor)
            search.inclusion?.let { inclusion ->
                journal.settleIncluded(submission)
                progress.remove(attemptId)
                return@withScope Outcome.Included(submission, inclusion)
            }
            before = search.nextBeforeHeight
            if (before == null) {
                progress.remove(attemptId)
                if (!windowFinalized) return@withScope Outcome.Unsettled(submission, expiresAfterBlock = lastValidBlock)
                journal.release(submission)
                return@withScope Outcome.Expired(submission)
            }
        }
        if (windowFinalized) progress[attemptId] = checkNotNull(before)
        Outcome.Unsettled(submission, expiresAfterBlock = lastValidBlock)
    }

    sealed interface Outcome {
        /** No unsettled transfer is kept for the scope. */
        data object Settled : Outcome

        data class Included(
            val submission: CrossChainSubmissionJournal.Submission,
            val inclusion: FinalizedExtrinsicReader.Inclusion
        ) : Outcome

        data class Expired(val submission: CrossChainSubmissionJournal.Submission) : Outcome

        /** Not found so far. It can still be included up to [expiresAfterBlock], when that block is known. */
        data class Unsettled(
            val submission: CrossChainSubmissionJournal.Submission,
            val expiresAfterBlock: BigInteger?
        ) : Outcome
    }

    private companion object {
        const val PAGE_SIZE = 64
        const val MAX_PAGES = 8
    }
}
