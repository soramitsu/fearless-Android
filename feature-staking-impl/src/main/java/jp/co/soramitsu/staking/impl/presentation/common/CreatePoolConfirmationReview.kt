package jp.co.soramitsu.staking.impl.presentation.common

import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The local create review owns its immutable fields, publication and presentation lifetime. */
class CreatePoolConfirmationReview internal constructor(
    private val lock: Any,
    private val mainState: StakingPoolSharedState<StakingPoolState>,
    private val createState: StakingPoolSharedState<StakingPoolCreateFlowState>
) {
    private val draft = createState.capture()
    val fields = requireNotNull(draft.value)
    private val context = fields.context
    val main = requireNotNull(context.value)
    private var active = true
    private var successPresentationUsed = false
    private var successPresentationReceipt: StakingPoolSharedState.Snapshot<StakingPoolCreateFlowState>? = null
    private var completion: StakingPoolSharedState.Snapshot<StakingPoolCreateFlowState>? = null

    fun isCurrent(): Boolean = synchronized(lock) {
        active && isContextCurrent(completion ?: draft)
    }

    fun requireCurrent() {
        if (!isCurrent()) throw CancellationException("Pool create review changed")
    }

    /** A departed review cannot navigate again, even if the same wallet or draft returns. */
    fun revoke(): Boolean = synchronized(lock) {
        val wasActive = active
        active = false
        wasActive
    }

    /** Only synchronous presentation runs here; never hold the selection lock across network work. */
    fun present(operation: () -> Unit) {
        synchronized(lock) {
            val receipt = successPresentationReceipt
            val current = if (receipt != null) isContextCurrent(receipt) else isCurrent()
            if (current) WalletSelectionEpoch.runIfCurrentOrNull(requireNotNull(main.walletSelectionEpoch), operation)
        }
    }

    /** A once-only synchronous success sequence may pop its own ViewModel before its next step. */
    fun withSuccessPresentation(operation: () -> Unit) {
        synchronized(lock) {
            val receipt = completion ?: return
            if (successPresentationUsed || !isCurrent()) return
            WalletSelectionEpoch.runIfCurrentOrNull(requireNotNull(main.walletSelectionEpoch)) {
                successPresentationUsed = true
                successPresentationReceipt = receipt
                try {
                    operation()
                } finally {
                    successPresentationReceipt = null
                }
            }
        }
    }

    private fun isContextCurrent(receipt: StakingPoolSharedState.Snapshot<StakingPoolCreateFlowState>): Boolean =
        mainState.isCurrent(context) && createState.isCurrent(receipt) &&
            main.walletSelectionEpoch?.let(WalletSelectionEpoch::isCurrent) == true

    /** Synchronous acknowledgement consumes only this exact attempt, including after departure. */
    class Execution internal constructor(val authorize: (String) -> MutationExecutionGuard, val onSubmitted: () -> Unit)

    /** Claims the original draft and binds its physical key/sign/output authority to the active review. */
    suspend fun execute(operation: suspend (Execution) -> Result<String>): Result<String> {
        val job = currentCoroutineContext()
        val claim = synchronized(lock) {
            requireCurrent()
            if (completion != null) throw CancellationException("Pool create review already completed")
            WalletSelectionEpoch.runIfCurrentOrNull(requireNotNull(main.walletSelectionEpoch)) {
                job.ensureActive()
                val acquired = createState.tryClaim(draft)
                    ?: throw CancellationException("Pool create review already claimed")
                try {
                    // Claim publication can synchronously resume an observer that changes context.
                    requireCurrent()
                    if (!createState.owns(acquired)) throw CancellationException("Pool create claim changed")
                    job.ensureActive()
                    acquired
                } catch (error: Throwable) {
                    createState.release(acquired)
                    throw error
                }
            } ?: throw CancellationException("Wallet selection changed")
        }
        val submitted: () -> Unit = {
            synchronized(lock) {
                if (completion == null) completion = createState.consume(claim)
            }
        }
        try {
            val result = operation(Execution(authorize = { intent ->
                object : MutationExecutionGuard {
                    private var submissionAdmitted = false

                    override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T = synchronized(lock) {
                        check(intentSha256 == intent) { "Pool create intent changed" }
                        requireCurrent()
                        check(createState.owns(claim)) { "Pool create claim changed" }
                        // Same pool -> wallet ordering as context publication. No suspended work here.
                        WalletSelectionEpoch.runIfCurrent(requireNotNull(main.walletSelectionEpoch)) {
                            job.ensureActive()
                            operation()
                        }
                    }

                    override fun <T> runSubmissionIfAuthorized(intentSha256: String, operation: () -> T): T {
                        runIfAuthorized(intentSha256) {
                            check(!submissionAdmitted) { "Pool create submission already admitted" }
                            submissionAdmitted = true
                        }
                        // The writer owns frozen bytes. Blocking output must not hold navigation locks.
                        return operation()
                    }
                }
            }, onSubmitted = submitted))
            if (result.isSuccess) submitted()
            return result
        } finally {
            createState.release(claim)
        }
    }
}
