package jp.co.soramitsu.staking.impl.presentation.common

import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

/** Local authority for the review displayed to the user, revocable at each physical operation. */
class JoinPoolExecutionReview internal constructor(
    private val lock: Any,
    private val mainState: StakingPoolSharedState<StakingPoolState>,
    private val joinState: StakingPoolSharedState<StakingPoolJoinFlowState>,
    private val context: StakingPoolSharedState.Snapshot<StakingPoolState>
) {
    private var active = true

    fun isActive(): Boolean = synchronized(lock) { active }

    /** Returns true only for the first departure, preserving idempotent Back navigation. */
    fun revoke(): Boolean = synchronized(lock) {
        val wasActive = active
        active = false
        wasActive
    }

    /** Binds one prepared payload and one attempt; no locks are held across suspended preparation. */
    fun authorize(
        claim: StakingPoolSharedState.Claim<StakingPoolJoinFlowState>,
        intent: String,
        job: Job
    ): MutationExecutionGuard = object : MutationExecutionGuard {
        private var submissionAdmitted = false

        override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T = synchronized(lock) {
            check(intentSha256 == intent) { "Pool transaction intent changed" }
            check(active && mainState.isCurrent(context) && joinState.owns(claim)) { "Pool review changed" }
            val epoch = checkNotNull(context.value?.walletSelectionEpoch) { "Wallet selection is changing" }
            // Always pool -> wallet. State publication may synchronously resume observers using this order.
            WalletSelectionEpoch.runIfCurrent(epoch) {
                job.ensureActive()
                operation()
            }
        }

        override fun <T> runSubmissionIfAuthorized(intentSha256: String, operation: () -> T): T {
            runIfAuthorized(intentSha256) {
                check(!submissionAdmitted) { "Pool submission already admitted" }
                submissionAdmitted = true
            }
            // No queue, encoding or suspend point here. The writer already owns the frozen frame.
            // Output can block: an admitted write must never hold navigation or selection locks.
            return operation()
        }
    }
}
