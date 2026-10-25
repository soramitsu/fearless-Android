package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reads one retained scope without creating, claiming or consuming a new join draft. */
@HiltViewModel
class PoolJoinRecoveryViewModel @Inject constructor(
    private val resources: ResourceManager,
    private val shared: StakingPoolSharedStateProvider,
    private val interactor: StakingPoolInteractor,
    private val router: StakingRouter,
    private val selection: StakingSharedState
) : BaseViewModel() {
    private val main = shared.mainState.capture()
    private val context = main.value
    private val selected = selection.selectionRevision.capture()
    private val lifetime = shared.joinExecutionReview(main)
    private var recovery = Recovery()
    private val mutableViewState = MutableStateFlow(recovery.toViewState())
    val viewState = mutableViewState.asStateFlow()

    init {
        launch {
            if (!isCurrent()) {
                publish(Recovery(loaded = true, failed = true))
                return@launch
            }
            try {
                val payment = interactor.pendingJoin(context!!.requireChain, context.requireAddress, context.requireAsset.metaId)
                if (isCurrent()) publish(Recovery(payment = payment, loaded = true))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isCurrent()) publish(Recovery(loaded = true, failed = true))
            }
        }
    }

    /** Recent searches restart at the latest finalized head; older searches use only the retained cursor. */
    fun onCheckPending(older: Boolean = false) {
        val retained = recovery
        val payment = retained.payment ?: return
        if (!isCurrent() || retained.checking || retained.finalized || payment.anchor == null) return
        val before = if (older) retained.nextBefore ?: return else null
        val completed = AtomicBoolean(false)
        publish(retained.copy(checking = true, failed = false))
        launch {
            try {
                if (!isCurrent()) return@launch
                val result = interactor.reconcileJoin(context!!.requireChain, context.requireAddress,
                    context.requireAsset.metaId, payment.attemptId, before) { completed.set(true) }.getOrThrow()
                if (isCurrent()) publish(retained.copy(checked = true, failed = false,
                    finalized = completed.get(), nextBefore = result.search.nextBeforeHeight))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isCurrent()) publish(retained.copy(failed = true))
            } finally {
                // The durable archive acknowledgement can precede cancelled IO dispatch-back.
                // It belongs to the retained transaction and never consumes a different join draft.
                if (isCurrent()) {
                    if (completed.get()) publish(retained.copy(finalized = true, checked = true, nextBefore = null))
                    else if (recovery.checking) publish(retained)
                }
            }
        }
    }

    /** Inclusion is not dispatch success; no-pending is a distinct local result. */
    fun onReturnToPool() {
        if (isCurrent() && (recovery.finalized || (recovery.loaded && !recovery.failed && recovery.payment == null)) &&
            lifetime.revoke()) router.back()
    }

    fun onBackClick() {
        if (lifetime.revoke()) router.back()
    }

    fun onDismissed() {
        lifetime.revoke()
    }

    override fun onCleared() {
        lifetime.revoke()
        super.onCleared()
    }

    private fun isCurrent(): Boolean = context?.chain != null && context.asset != null && context.address != null &&
        lifetime.isActive() && shared.isJoinContextCurrent(main) &&
        selection.selectionRevision.matches(selected, context.requireChain.id, context.requireAsset.token.configuration.id)

    private fun publish(value: Recovery) {
        recovery = value
        mutableViewState.value = value.toViewState()
    }

    private fun Recovery.toViewState(): PoolJoinRecoveryViewState {
        val message = when {
            !loaded -> R.string.pool_join_recovery_loading
            finalized -> R.string.pool_join_recovery_finalized
            failed -> R.string.pool_join_recovery_unavailable
            payment == null -> R.string.pool_join_recovery_empty
            payment.anchor == null -> R.string.pool_join_recovery_legacy
            checking -> R.string.pool_join_recovery_checking
            checked -> R.string.pool_join_recovery_not_found
            else -> R.string.pool_join_recovery_pending
        }
        return PoolJoinRecoveryViewState(
            message = resources.getString(message),
            amount = payment?.let { value -> context?.asset?.let {
                it.token.amountFromPlanks(value.amount.toBigInteger()).formatCryptoDetail(it.token.configuration.symbol)
            } },
            poolId = payment?.poolId,
            network = context?.chain?.name.orEmpty(),
            address = context?.address.orEmpty(),
            transactionHash = payment?.transactionHash,
            canCheck = payment?.anchor != null && !checking && !finalized,
            canCheckOlder = payment?.anchor != null && nextBefore != null && !checking && !finalized,
            finalized = finalized,
            canReturn = finalized || (loaded && !failed && payment == null),
            create = payment?.create
        )
    }

    private data class Recovery(val payment: PoolJoinJournal.Payment? = null, val loaded: Boolean = false,
        val checking: Boolean = false, val checked: Boolean = false, val failed: Boolean = false,
        val finalized: Boolean = false, val nextBefore: BigInteger? = null)
}
