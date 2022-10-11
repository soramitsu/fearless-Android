package jp.co.soramitsu.staking.impl.presentation.confirm.pool.create

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.GetIdentitiesUseCase
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseConfirmViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ConfirmCreatePoolViewModel @Inject constructor(
    poolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    resourceManager: ResourceManager,
    private val router: StakingRouter,
    private val getIdentities: GetIdentitiesUseCase
) : BaseConfirmViewModel(
    address = poolSharedStateProvider.requireMainState.requireAddress,
    resourceManager = resourceManager,
    asset = poolSharedStateProvider.requireMainState.requireAsset,
    amountInPlanks = poolSharedStateProvider.requireCreateState.requireAmountInPlanks,
    feeEstimator = { stakingPoolInteractor.estimateCreatePoolFee(poolSharedStateProvider) },
    executeOperation = { address, _ -> stakingPoolInteractor.createPool(poolSharedStateProvider, address) },
    onOperationSuccess = { router.returnToManagePoolStake() },
    accountNameProvider = {
        val chain = poolSharedStateProvider.requireMainState.requireChain
        getIdentities(chain, it).mapNotNull { pair ->
            pair.value?.display
        }.firstOrNull()
    },
    titleRes = R.string.pool_stakeng_create_confirm_title
) {
    override val tableItemsFlow: StateFlow<List<TitleValueViewState>> = addressViewStateFlow.map { addressState ->
        val createState = poolSharedStateProvider.requireCreateState

        val poolId = TitleValueViewState(resourceManager.getString(R.string.pool_staking_pool_id), createState.requirePoolId.toString())
        val depositor = TitleValueViewState(resourceManager.getString(R.string.pool_staking_depositor), address)
        val root = TitleValueViewState(resourceManager.getString(R.string.pool_staking_root), address)
        val nominator = TitleValueViewState(resourceManager.getString(R.string.pool_staking_nominator), createState.requireNominatorAddress)
        val stateToggler = TitleValueViewState(resourceManager.getString(R.string.pool_staking_nominator), createState.requireStateTogglerAddress)
        listOf(addressState, amountViewState, poolId, depositor, root, nominator, stateToggler)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onBackClick() {
        router.back()
    }
}

private suspend fun StakingPoolInteractor.estimateCreatePoolFee(poolSharedStateProvider: StakingPoolSharedStateProvider): BigInteger {
    val createFlowState = poolSharedStateProvider.requireCreateState
    val address = poolSharedStateProvider.requireMainState.requireAddress

    return estimateCreateFee(
        createFlowState.requirePoolId.toBigInteger(),
        createFlowState.requirePoolName,
        createFlowState.requireAmountInPlanks,
        address,
        createFlowState.requireNominatorAddress,
        createFlowState.requireStateTogglerAddress
    )
}

private suspend fun StakingPoolInteractor.createPool(poolSharedStateProvider: StakingPoolSharedStateProvider, address: String): Result<String> {
    val createFlowState = poolSharedStateProvider.requireCreateState

    return createPool(
        createFlowState.requirePoolId.toBigInteger(),
        createFlowState.requirePoolName,
        createFlowState.requireAmountInPlanks,
        address,
        createFlowState.requireNominatorAddress,
        createFlowState.requireStateTogglerAddress
    )
}
