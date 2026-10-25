package jp.co.soramitsu.staking.impl.presentation.common

import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.math.BigInteger

/** Authority for one member amount screen, including work suspended before its confirmation opens. */
class PoolManagePreparationReview internal constructor(
    private val lock: Any,
    private val mainState: StakingPoolSharedState<StakingPoolState>,
    private val manageState: StakingPoolSharedState<StakingPoolManageFlowState>
) {
    private var draft = manageState.capture()
    val fields = requireNotNull(draft.value)
    private val context = fields.context
    val main = requireNotNull(context.value)
    private var active = manageState.isEditable(draft)
    private var navigationUsed = false
    private var awaitingResult = false
    private var pending: Preparation? = null

    class Preparation internal constructor(internal val draft: StakingPoolSharedState.Snapshot<StakingPoolManageFlowState>)

    private fun hasOriginalContext(): Boolean = active && mainState.isCurrent(context) &&
        main.walletSelectionEpoch?.let(WalletSelectionEpoch::isCurrent) == true

    private fun isReadable(): Boolean = hasOriginalContext() && manageState.isCurrent(draft)

    private fun isCurrent(): Boolean = isReadable() && manageState.isEditable(draft)

    /** A temporary confirmation claim must not permanently cancel the original screen's fee collector. */
    fun requireReadable() = synchronized(lock) {
        if (!isReadable()) throw CancellationException("Pool member context changed")
    }

    fun requireCurrent() = synchronized(lock) {
        if (!isCurrent()) throw CancellationException("Pool member preparation changed")
    }

    /** An actual return to this screen may edit its own unchanged draft after a failed confirmation. */
    fun resume() = synchronized(lock) {
        if (active && mainState.isCurrent(context) && manageState.isCurrent(draft)) {
            val current = manageState.capture()
            if (manageState.isEditable(current)) {
                draft = current
                navigationUsed = false
                pending = null
            }
        }
    }

    /** Typing or a quick input withdraws pending work for the prior visible amount. */
    fun amountChanged() = synchronized(lock) {
        pending = null
        awaitingResult = false
    }

    fun revoke(): Boolean = synchronized(lock) {
        val wasActive = active
        active = false
        pending = null
        awaitingResult = false
        wasActive
    }

    fun goBack(operation: () -> Unit) = synchronized(lock) {
        val mayNavigate = hasOriginalContext() && !navigationUsed
        revoke()
        if (mayNavigate) WalletSelectionEpoch.runIfCurrentOrNull(requireNotNull(main.walletSelectionEpoch), operation)
        Unit
    }

    fun present(operation: () -> Unit) = synchronized(lock) {
        if (isCurrent() && !navigationUsed && pending == null) {
            WalletSelectionEpoch.runIfCurrentOrNull(requireNotNull(main.walletSelectionEpoch), operation)
        }
        Unit
    }

    /** Only the alert opened by this preparation may deliver its one follow-up action. */
    fun presentResult(operation: () -> Unit) = synchronized(lock) {
        if (awaitingResult && isCurrent()) {
            awaitingResult = false
            WalletSelectionEpoch.runIfCurrentOrNull(requireNotNull(main.walletSelectionEpoch), operation)
        }
        Unit
    }

    /** Only one pending preparation or forward navigation is admitted until the screen actually resumes. */
    suspend fun prepare(operation: suspend (Preparation) -> Unit) {
        currentCoroutineContext().ensureActive()
        val attempt = synchronized(lock) {
            requireCurrent()
            if (pending != null || navigationUsed) throw CancellationException("Pool member preparation unavailable")
            awaitingResult = false
            Preparation(draft).also { pending = it }
        }
        try { operation(attempt) } finally {
            synchronized(lock) { if (pending === attempt) pending = null }
        }
    }

    /** Publishes only an owned edit and checks again after synchronous publication observers. */
    suspend fun continueTo(
        attempt: Preparation,
        amount: BigInteger? = null,
        expectsResult: Boolean = false,
        operation: () -> Unit
    ) {
        val job = currentCoroutineContext()
        synchronized(lock) {
            job.ensureActive()
            requireCurrent()
            if (pending !== attempt || !manageState.isEditable(attempt.draft)) {
                throw CancellationException("Pool member preparation replaced")
            }
            WalletSelectionEpoch.runIfCurrentOrNull(requireNotNull(main.walletSelectionEpoch)) {
                if (amount != null) {
                    draft = manageState.editIfCurrent(attempt.draft, fields.copy(amountInPlanks = amount))
                        ?: throw CancellationException("Pool member edit replaced")
                }
                requireCurrent()
                // An edit observer can change the visible amount without replacing this publication.
                if (pending !== attempt) throw CancellationException("Pool member amount changed during publication")
                job.ensureActive()
                navigationUsed = true
                awaitingResult = expectsResult
                operation()
            } ?: throw CancellationException("Wallet selection changed")
        }
    }
}
