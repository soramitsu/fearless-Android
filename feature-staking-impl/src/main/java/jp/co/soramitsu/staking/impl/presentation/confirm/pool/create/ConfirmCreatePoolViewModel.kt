package jp.co.soramitsu.staking.impl.presentation.confirm.pool.create

import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicBoolean
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal
import jp.co.soramitsu.staking.impl.presentation.confirm.pool.join.PoolJoinRecoveryViewState
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingConfirmViewModel
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.CreatePoolConfirmationReview
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ConfirmCreatePoolViewModel private constructor(
    walletInteractor: WalletInteractor,
    existentialDepositUseCase: ExistentialDepositUseCase,
    poolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter,
    private val poolInteractor: StakingPoolInteractor,
    private val review: CreatePoolConfirmationReview,
    private val submitted: MutableStateFlow<Boolean> = MutableStateFlow(false)
) : StakingConfirmViewModel(
    walletInteractor = walletInteractor,
    existentialDepositUseCase = existentialDepositUseCase,
    chain = review.main.requireChain,
    router = router,
    address = review.main.requireAddress,
    resourceManager = resourceManager,
    asset = review.main.requireAsset,
    amountInPlanks = review.fields.requireAmountInPlanks,
    customIcon = R.drawable.ic_vector,
    feeEstimator = {
        review.requireCurrent()
        stakingPoolInteractor.estimateCreatePoolFee(review)
    },
    executeOperation = { address, _ ->
        review.execute { execution -> stakingPoolInteractor.createPool(review, address, execution) { submitted.value = true } }
    },
    // A node acknowledgement is not evidence that pool creation dispatched successfully.
    onOperationSuccess = {},
    accountNameProvider = { stakingPoolInteractor.getAccountName(it) },
    titleRes = R.string.pool_staking_create_confirm_title,
    withCurrentPresentation = review::present,
    withSuccessPresentation = {
        // Replace the generic success/validator handoff with the original durable review.
        review.withSuccessPresentation { submitted.value = true }
    }
) {
    @Inject constructor(
        walletInteractor: WalletInteractor,
        existentialDepositUseCase: ExistentialDepositUseCase,
        poolSharedStateProvider: StakingPoolSharedStateProvider,
        stakingPoolInteractor: StakingPoolInteractor,
        resourceManager: ResourceManager,
        router: StakingRouter,
        poolInteractor: StakingPoolInteractor
    ) : this(walletInteractor, existentialDepositUseCase, poolSharedStateProvider, stakingPoolInteractor,
        resourceManager, router, poolInteractor, poolSharedStateProvider.createConfirmationReview())

    private val addressDisplayFlow = flowOf {
        poolInteractor.getAccountName(address) ?: address
    }.inBackground()

    private val nominatorDisplayFlow = flowOf {
        val nominatorAddress = review.fields.requireNominatorAddress
        poolInteractor.getAccountName(nominatorAddress) ?: nominatorAddress
    }.inBackground()

    private val stateTogglerDisplayFlow = flowOf {
        val stateTogglerAddress = review.fields.requireStateTogglerAddress
        poolInteractor.getAccountName(stateTogglerAddress) ?: stateTogglerAddress
    }.inBackground()

    override val tableItemsFlow: StateFlow<List<TitleValueViewState>> = combine(
        addressDisplayFlow,
        nominatorDisplayFlow,
        stateTogglerDisplayFlow,
        feeViewStateFlow
    ) { addressDisplay, nominatorDisplay, stateTogglerDisplay, feeViewState ->
        val createState = review.fields

        val poolId = TitleValueViewState(resourceManager.getString(R.string.pool_staking_pool_id), createState.requirePoolId.toString())
        val addressState = defaultAddressViewState.copy(value = addressDisplay)
        val depositor = TitleValueViewState(resourceManager.getString(R.string.pool_staking_depositor), addressDisplay)
        val root = TitleValueViewState(resourceManager.getString(R.string.pool_staking_root), addressDisplay)
        val nominator = TitleValueViewState(resourceManager.getString(R.string.pool_staking_nominator), nominatorDisplay)
        val stateToggler = TitleValueViewState(resourceManager.getString(R.string.pool_recovery_state_control), stateTogglerDisplay)
        listOf(addressState, amountViewState, poolId, depositor, root, nominator, stateToggler, feeViewState)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private data class Recovery(val payment: PoolJoinJournal.Payment?, val checking: Boolean = false,
        val checked: Boolean = false, val failed: Boolean = false, val finalized: Boolean = false,
        val nextBefore: BigInteger? = null)

    private var recovery: Recovery? = null
    private val mutableRecoveryViewState = MutableStateFlow<PoolJoinRecoveryViewState?>(null)
    val recoveryViewState = mutableRecoveryViewState.asStateFlow()

    init {
        // This reads local retained bytes even when remote fee estimation is unavailable.
        launch { loadRecovery() }
        launch { submitted.filter { it }.collect { loadRecovery() } }
    }

    private suspend fun loadRecovery(): Boolean {
        if (recovery != null) return true
        review.requireCurrent()
        try {
            val payment = poolInteractor.pendingJoin(chain, address, asset.metaId)
            if (payment != null) review.present { if (recovery == null) publishRecovery(Recovery(payment)) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            review.present { if (recovery == null) publishRecovery(Recovery(null, failed = true)) }
        }
        return recovery != null
    }

    override suspend fun isValid(): Result<Any> {
        review.requireCurrent()
        if (loadRecovery()) throw CancellationException("Previous pool transaction requires review")
        review.requireCurrent()
        return super.isValid().also { review.requireCurrent() }
    }

    override fun showError(throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        launch {
            if (!loadRecovery()) review.present { super.showError(throwable) }
        }
    }

    /** Read-only reconciliation never creates a new draft or resubmits retained output. */
    fun onCheckPending(older: Boolean = false) {
        val retained = recovery ?: return
        val payment = retained.payment ?: return
        if (!review.isCurrent() || retained.checking || retained.finalized || payment.anchor == null) return
        val before = if (older) retained.nextBefore ?: return else null
        val completed = AtomicBoolean(false)
        publishRecovery(retained.copy(checking = true, failed = false))
        launch {
            try {
                review.requireCurrent()
                val result = poolInteractor.reconcileJoin(chain, address, asset.metaId, payment.attemptId, before) {
                    completed.set(true)
                }.getOrThrow()
                review.present { publishRecovery(retained.copy(checked = true, failed = false,
                    finalized = completed.get(), nextBefore = result.search.nextBeforeHeight)) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                review.present { publishRecovery(retained.copy(failed = true)) }
            } finally {
                // A durable archive callback can precede cancelled dispatch back to this screen.
                review.present {
                    if (completed.get()) publishRecovery(retained.copy(finalized = true, checked = true, nextBefore = null))
                    else if (recovery?.checking == true) publishRecovery(retained)
                }
            }
        }
    }

    /** Inclusion is not dispatch success. Leave the consumed review for Earn before preparing another creation. */
    fun onReturnToPool() {
        if (recovery?.finalized == true) review.present {
            if (review.revoke()) router.returnToMain()
        }
    }

    private fun publishRecovery(value: Recovery) {
        recovery = value
        val payment = value.payment
        val message = when {
            value.finalized -> R.string.pool_join_recovery_finalized
            payment == null || value.failed -> R.string.pool_join_recovery_unavailable
            payment.anchor == null -> R.string.pool_join_recovery_legacy
            value.checking -> R.string.pool_join_recovery_checking
            value.checked -> R.string.pool_join_recovery_not_found
            else -> R.string.pool_join_recovery_pending
        }
        mutableRecoveryViewState.value = PoolJoinRecoveryViewState(
            message = resourceManager.getString(message),
            amount = payment?.let { asset.token.amountFromPlanks(it.amount.toBigInteger()).formatCryptoDetail(asset.token.configuration.symbol) },
            poolId = payment?.poolId, network = chain.name, address = address, transactionHash = payment?.transactionHash,
            canCheck = payment?.anchor != null && !value.checking && !value.finalized,
            canCheckOlder = payment?.anchor != null && value.nextBefore != null && !value.checking && !value.finalized,
            finalized = value.finalized, create = payment?.create, returnLabelRes = R.string.pool_recovery_return_earn
        )
    }

    fun onBackClick() {
        if (review.revoke()) router.back()
    }

    /** Dialog departure revokes callbacks; configuration recreation keeps the same ViewModel review. */
    fun onDismissed() {
        review.revoke()
    }

    override fun onCleared() {
        review.revoke()
        super.onCleared()
    }
}

private suspend fun StakingPoolInteractor.estimateCreatePoolFee(review: CreatePoolConfirmationReview): BigInteger {
    val createFlowState = review.fields
    val address = review.main.requireAddress

    return estimateCreateFee(
        review.main.requireChain,
        review.main.requireAsset.metaId,
        createFlowState.requirePoolId.toBigInteger(),
        createFlowState.requirePoolName,
        createFlowState.requireAmountInPlanks,
        address,
        createFlowState.requireNominatorAddress,
        createFlowState.requireStateTogglerAddress
    )
}

private suspend fun StakingPoolInteractor.createPool(
    review: CreatePoolConfirmationReview,
    address: String,
    execution: CreatePoolConfirmationReview.Execution,
    onAcknowledged: () -> Unit
): Result<String> {
    val createFlowState = review.fields

    return createPool(
        review.main.requireChain,
        review.main.requireAsset.metaId,
        createFlowState.requirePoolId.toBigInteger(),
        createFlowState.requirePoolName,
        createFlowState.requireAmountInPlanks,
        address,
        createFlowState.requireNominatorAddress,
        createFlowState.requireStateTogglerAddress,
        execution.authorize,
        {
            execution.onSubmitted()
            // This callback survives cancellation of the attempt's IO return, but owns no navigation.
            onAcknowledged()
        }
    )
}
