package jp.co.soramitsu.staking.impl.presentation.common

import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Local member review authority. Downstream signing and durable output remain separate boundaries. */
class PoolManageConfirmationReview internal constructor(
    private val lock: Any,
    private val mainState: StakingPoolSharedState<StakingPoolState>,
    private val manageState: StakingPoolSharedState<StakingPoolManageFlowState>
) {
    private val draft = manageState.capture()
    val fields = requireNotNull(draft.value)
    private val context = fields.context
    val main = requireNotNull(context.value)
    private var active = true
    private var completion: StakingPoolSharedState.Snapshot<StakingPoolManageFlowState>? = null
    private var successUsed = false
    private var presentingSuccess = false

    private fun contextCurrent(): Boolean = mainState.isCurrent(context) &&
        manageState.isCurrent(completion ?: draft) && main.walletSelectionEpoch?.let(WalletSelectionEpoch::isCurrent) == true

    fun requireCurrent() = synchronized(lock) {
        if (!active || !contextCurrent()) throw CancellationException("Pool member review changed")
    }

    fun revoke(): Boolean = synchronized(lock) {
        val wasActive = active
        active = false
        wasActive
    }

    fun present(operation: () -> Unit) = synchronized(lock) {
        if ((active || presentingSuccess) && contextCurrent()) {
            WalletSelectionEpoch.runIfCurrentOrNull(requireNotNull(main.walletSelectionEpoch), operation)
        }
        Unit
    }

    /** A synchronous success handoff can clear its own ViewModel but cannot act on a replacement context. */
    fun withSuccessPresentation(operation: () -> Unit) = synchronized(lock) {
        if (completion == null || successUsed || !active || !contextCurrent()) return@synchronized
        WalletSelectionEpoch.runIfCurrentOrNull(requireNotNull(main.walletSelectionEpoch)) {
            successUsed = true
            presentingSuccess = true
            try { operation() } finally { presentingSuccess = false }
        }
        Unit
    }

    /** Claims across all instances and consumes only this original draft when success is observed. */
    suspend fun execute(operation: suspend () -> Result<String>): Result<String> {
        val job = currentCoroutineContext()
        val claim = synchronized(lock) {
            requireCurrent()
            if (completion != null) throw CancellationException("Pool member review completed")
            WalletSelectionEpoch.runIfCurrentOrNull(requireNotNull(main.walletSelectionEpoch)) {
                job.ensureActive()
                val acquired = manageState.tryClaim(draft) ?: throw CancellationException("Pool member review unavailable")
                try {
                    requireCurrent()
                    if (!manageState.owns(acquired)) throw CancellationException("Pool member claim changed")
                    job.ensureActive()
                    acquired
                } catch (error: Throwable) {
                    manageState.release(acquired)
                    throw error
                }
            } ?: throw CancellationException("Wallet selection changed")
        }
        try {
            val result = operation()
            if (result.isSuccess) synchronized(lock) { completion = manageState.consumeKeepingValue(claim) }
            return result
        } finally {
            manageState.release(claim)
        }
    }
}
