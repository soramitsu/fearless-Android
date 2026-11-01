package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionJournal
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionReconciler
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionTracking
import jp.co.soramitsu.xcm.XcmTrackedSubmission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cross-chain and bridge transfers share one unsettled-transfer record per wallet, origin network and sender
 * account: a new transfer is signed only when no earlier one of that scope is unsettled.
 */
class CrossChainSubmissions(
    private val journal: CrossChainSubmissionJournal,
    private val finality: FinalizedExtrinsicReader,
    private val reconciler: CrossChainSubmissionReconciler = CrossChainSubmissionReconciler(journal, finality)
) {
    fun scope(walletId: Long, originChain: Chain, senderAccountId: ByteArray) = CrossChainSubmissionJournal.Scope(
        walletId = walletId,
        chainId = originChain.id.removePrefix("0x").lowercase(),
        accountId = senderAccountId.toHexString(withPrefix = false)
    )

    suspend fun pending(scope: CrossChainSubmissionJournal.Scope): CrossChainSubmissionJournal.Submission? =
        withContext(Dispatchers.IO) { journal.read(scope) }

    /**
     * Runs [block] with a tracked submission that retains the signed bytes before they are sent. Fails with
     * [jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionPendingException] while an earlier transfer of
     * [scope] is unsettled.
     */
    suspend fun <T> send(
        scope: CrossChainSubmissionJournal.Scope,
        details: CrossChainSubmissionJournal.Details,
        signingProvider: KeypairProvider,
        block: suspend (XcmTrackedSubmission) -> T
    ): T = withContext(Dispatchers.IO) {
        journal.withScope(scope) {
            journal.requireAvailable(scope)
            // Captured before signing: only blocks after it can hold this attempt.
            val anchor = finality.anchor(scope.chainId)
            val tracking = CrossChainSubmissionTracking(journal, scope, details, anchor)
            block(XcmTrackedSubmission(scope.walletId, signingProvider, tracking))
        }
    }

    suspend fun check(scope: CrossChainSubmissionJournal.Scope, attemptId: String): CrossChainSubmissionReconciler.Outcome =
        withContext(Dispatchers.IO) { reconciler.check(scope, attemptId) }

    /** The last transfer of [scope] proven included, kept until the user dismisses it. */
    suspend fun included(scope: CrossChainSubmissionJournal.Scope): CrossChainSubmissionJournal.Submission? =
        withContext(Dispatchers.IO) { journal.readIncluded(scope) }

    suspend fun dismissIncluded(submission: CrossChainSubmissionJournal.Submission) = withContext(Dispatchers.IO) {
        journal.withScope(submission.scope) { journal.dismissIncluded(submission) }
    }
}
