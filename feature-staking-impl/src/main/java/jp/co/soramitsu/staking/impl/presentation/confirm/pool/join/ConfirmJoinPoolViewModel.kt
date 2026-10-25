package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolJoinFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

@HiltViewModel
class ConfirmJoinPoolViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val poolInteractor: StakingPoolInteractor,
    private val router: StakingRouter
) : BaseViewModel() {

    private val chain: Chain
    private val asset: Asset
    private val amount: BigDecimal
    private val selectedPool: PoolInfo
    private val address: String
    private val joinReview = stakingPoolSharedStateProvider.joinFlowState.capture()
    private val mainReview = requireNotNull(joinReview.value).context

    init {
        val setupState = requireNotNull(joinReview.value)
        val mainState = requireNotNull(mainReview.value)
        chain = requireNotNull(mainState.chain)
        asset = requireNotNull(mainState.asset)
        amount = requireNotNull(setupState.amount)
        selectedPool = requireNotNull(setupState.selectedPool)
        address = requireNotNull(mainState.address)
    }

    private val toolbarViewState = ToolbarViewState(
        resourceManager.getString(R.string.common_confirm),
        R.drawable.ic_arrow_back_24dp
    )

    private val addressViewState = TitleValueViewState(
        resourceManager.getString(R.string.transaction_details_from),
        resourceManager.getString(R.string.pool_account_for_join),
        address
    )
    private val poolViewState = TitleValueViewState(
        resourceManager.getString(R.string.pool_staking_selected_pool),
        selectedPool.name,
        null
    )

    private val feeViewStateFlow = jp.co.soramitsu.common.utils.flowOf {
        val amountInPlanks = asset.token.planksFromAmount(amount)
        val feeInPlanks = poolInteractor.estimateJoinFee(chain, address, amountInPlanks, asset.metaId, selectedPool.poolId)
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatCryptoDetail(asset.token.configuration.symbol)
        val feeFiat = fee.formatFiat(asset.token.fiatSymbol)
        TitleValueViewState(
            resourceManager.getString(R.string.common_network_fee),
            feeFormatted,
            feeFiat
        )
    }
        .catch { emit(defaultFeeState) }
        .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        defaultFeeState
    )

    private val reviewActive = stakingPoolSharedStateProvider.joinExecutionReview(mainReview)
    private val recovery = MutableStateFlow<Recovery?>(null)
    private var finalizedReceipt: StakingPoolSharedState.Snapshot<StakingPoolJoinFlowState>? = null
    private val additionalMessageFlow = jp.co.soramitsu.common.utils.flowOf {
        if (poolInteractor.getValidatorsIds(chain, selectedPool.poolId).isEmpty()) {
            resourceManager.getString(R.string.pool_join_no_validators_message)
        } else null
    }.catch { emit(null) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val viewState = combine(feeViewStateFlow, stakingPoolSharedStateProvider.joinFlowState.submissionInProgress, recovery,
        additionalMessageFlow) { feeViewState, pending, retained, additionalMessage ->
        val isLoading = retained == null && pending && stakingPoolSharedStateProvider.joinFlowState.isCurrent(joinReview)
        val amount = this.amount.formatCryptoDetail(asset.token.configuration.symbol)

        ConfirmJoinPoolScreenViewState(
            toolbarViewState,
            amount,
            addressViewState,
            poolViewState,
            feeViewState,
            GradientIconState.Local(R.drawable.ic_vector),
            additionalMessage,
            isLoading,
            retained?.toViewState()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultScreenState)

    init {
        launch { loadRecovery() }
    }

    fun onBackClick() {
        if (reviewActive.revoke()) router.back()
    }

    /** Revokes presentation and queued callbacks while allowing known in-flight success to be recorded. */
    fun onDismissed() {
        reviewActive.revoke()
    }

    override fun onCleared() {
        reviewActive.revoke()
        super.onCleared()
    }

    fun onConfirm() {
        if (recovery.value != null || !reviewActive.isActive() || !stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview)) return
        val joinFlow = stakingPoolSharedStateProvider.joinFlowState
        val claim = joinFlow.tryClaim(joinReview) ?: return
        val completion = AtomicReference<StakingPoolSharedState.Snapshot<StakingPoolJoinFlowState>?>(null)
        launch {
            try {
                if (!reviewActive.isActive() || !stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) || !joinFlow.isCurrent(joinReview)) return@launch
                val amountInPlanks = asset.token.planksFromAmount(amount)
                val attemptJob = coroutineContext.job
                poolInteractor.joinPool(chain, address, amountInPlanks, selectedPool.poolId, asset.metaId,
                    authorize = { intent -> reviewActive.authorize(claim, intent, attemptJob) }
                ) {
                    // Synchronous shared state only: the API invokes this on IO before dispatching back.
                    completion.set(joinFlow.consume(claim))
                }.fold({
                    val receipt = completion.get()
                    if (reviewActive.isActive() && stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) && receipt != null && joinFlow.isCurrent(receipt)) {
                        router.returnToMain()
                        if (stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) && joinFlow.isCurrent(receipt)) router.openOperationSuccess(it, chain.id)
                    }
                }, {
                    if (it is CancellationException) throw it
                    if (reviewActive.isActive() && stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) && joinFlow.isCurrent(joinReview)) {
                        loadRecovery()
                        if (recovery.value == null && reviewActive.isActive() &&
                            stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) && joinFlow.isCurrent(joinReview)) showError(it)
                    }
                })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (reviewActive.isActive() && stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) && joinFlow.isCurrent(completion.get() ?: joinReview)) {
                    loadRecovery()
                    if (recovery.value == null && reviewActive.isActive() && stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) &&
                        joinFlow.isCurrent(completion.get() ?: joinReview)) showError(error)
                }
            }
        }.invokeOnCompletion {
            joinFlow.release(claim)
        }
    }

    /** Rechecks retained bytes only; this action never signs or resubmits an operation. */
    fun onCheckPending(older: Boolean = false) {
        val retained = recovery.value ?: return
        val payment = retained.payment ?: return
        if (retained.checking || retained.finalized || payment.anchor == null || !reviewActive.isActive() ||
            !stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview)) return
        val before = if (older) retained.nextBefore ?: return else null
        val joinFlow = stakingPoolSharedStateProvider.joinFlowState
        val claim = joinFlow.tryClaim(joinReview) ?: return
        val completion = AtomicReference<StakingPoolSharedState.Snapshot<StakingPoolJoinFlowState>?>(null)
        recovery.value = retained.copy(checking = true, failed = false)
        launch {
            try {
                if (!reviewActive.isActive() || !stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) || !joinFlow.isCurrent(joinReview)) return@launch
                val result = poolInteractor.reconcileJoin(chain, address, asset.metaId, payment.attemptId, before) {
                    completion.set(joinFlow.consume(claim))
                }.getOrThrow()
                val receipt = completion.get()
                if (reviewActive.isActive() && stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) && joinFlow.isCurrent(receipt ?: joinReview)) {
                    finalizedReceipt = receipt
                    recovery.value = retained.copy(finalized = result.search.inclusion != null,
                        checked = true, nextBefore = result.search.nextBeforeHeight)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (reviewActive.isActive() && stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) && joinFlow.isCurrent(joinReview)) {
                    recovery.value = retained.copy(failed = true)
                }
            } finally {
                // A durable archive may complete on IO just before cancellation discards dispatch-back.
                // Its synchronous receipt remains authoritative even when no Result reaches this UI.
                val receipt = completion.get()
                if (receipt != null && reviewActive.isActive() && stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) && joinFlow.isCurrent(receipt)) {
                    finalizedReceipt = receipt
                    recovery.value = retained.copy(finalized = true, checked = true, nextBefore = null)
                } else if (recovery.value?.checking == true) recovery.value = retained
            }
        }.invokeOnCompletion { joinFlow.release(claim) }
    }

    /** Inclusion is not dispatch success. Return to refreshed pool state before another review. */
    fun onReturnToPool() {
        val receipt = finalizedReceipt ?: return
        if (recovery.value?.finalized == true && reviewActive.isActive() &&
            stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) &&
            stakingPoolSharedStateProvider.joinFlowState.isCurrent(receipt) && reviewActive.revoke()) router.returnToMain()
    }

    private suspend fun loadRecovery() {
        try {
            val payment = poolInteractor.pendingJoin(chain, address, asset.metaId)
            if (payment != null && reviewActive.isActive() && stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) &&
                stakingPoolSharedStateProvider.joinFlowState.isCurrent(joinReview) && recovery.value == null) {
                recovery.value = Recovery(payment)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (reviewActive.isActive() && stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) &&
                stakingPoolSharedStateProvider.joinFlowState.isCurrent(joinReview) && recovery.value == null) recovery.value = Recovery(null, failed = true)
        }
    }

    private fun Recovery.toViewState(): PoolJoinRecoveryViewState {
        val message = when {
            finalized -> R.string.pool_join_recovery_finalized
            payment == null || failed -> R.string.pool_join_recovery_unavailable
            payment.anchor == null -> R.string.pool_join_recovery_legacy
            checking -> R.string.pool_join_recovery_checking
            checked -> R.string.pool_join_recovery_not_found
            else -> R.string.pool_join_recovery_pending
        }
        return PoolJoinRecoveryViewState(
            message = resourceManager.getString(message),
            amount = payment?.let { asset.token.amountFromPlanks(it.amount.toBigInteger()).formatCryptoDetail(asset.token.configuration.symbol) },
            poolId = payment?.poolId,
            network = chain.name,
            address = address,
            transactionHash = payment?.transactionHash,
            canCheck = payment?.anchor != null && !checking && !finalized,
            canCheckOlder = payment?.anchor != null && nextBefore != null && !checking && !finalized,
            finalized = finalized,
            create = payment?.create,
            returnLabelRes = R.string.pool_recovery_return_earn
        )
    }

    private data class Recovery(val payment: PoolJoinJournal.Payment?, val checking: Boolean = false,
        val checked: Boolean = false, val failed: Boolean = false, val finalized: Boolean = false,
        val nextBefore: BigInteger? = null)

    private val defaultFeeState
        get() = TitleValueViewState(
            resourceManager.getString(R.string.common_network_fee),
            null,
            null
        )

    private val defaultScreenState
        get() = ConfirmJoinPoolScreenViewState(
            toolbarViewState,
            "... ${asset.token.configuration.symbol}",
            addressViewState,
            poolViewState,
            defaultFeeState,
            GradientIconState.Local(R.drawable.ic_vector),
            isLoading = false
        )
}
