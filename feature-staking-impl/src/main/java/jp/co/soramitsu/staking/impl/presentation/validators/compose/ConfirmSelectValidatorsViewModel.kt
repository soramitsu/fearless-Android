package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ConfirmSelectValidatorsViewModel @Inject constructor(
    poolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    resourceManager: ResourceManager,
    private val router: StakingRouter,
    private val getIdentities: GetIdentitiesUseCase
) : BaseConfirmViewModel(
    address = poolSharedStateProvider.requireMainState.requireAddress,
    resourceManager = resourceManager,
    asset = poolSharedStateProvider.requireMainState.requireAsset,
    amountInPlanks = null,
    feeEstimator = {
        val poolId = poolSharedStateProvider.requireSelectValidatorsState.requirePoolId
        val validators = poolSharedStateProvider.requireSelectValidatorsState.selectedValidators.toTypedArray()
        require(validators.isNotEmpty())
        stakingPoolInteractor.estimateNominateFee(poolId, *validators)
    },
    executeOperation = { address, _ ->
        val poolId = poolSharedStateProvider.requireSelectValidatorsState.requirePoolId
        val validators = poolSharedStateProvider.requireSelectValidatorsState.selectedValidators.toTypedArray()
        require(validators.isNotEmpty())
        stakingPoolInteractor.nominate(poolId, address, *validators)
    },
    onOperationSuccess = { router.returnToMain() },
    accountNameProvider = {
        val chain = poolSharedStateProvider.requireMainState.requireChain
        getIdentities(chain, it).mapNotNull { pair ->
            pair.value?.display
        }.firstOrNull()
    },
    titleRes = R.string.staking_custom_validators_list_title
) {

    override val tableItemsFlow: StateFlow<List<TitleValueViewState>> = combine(addressViewStateFlow, feeViewStateFlow) { addressViewState, feeViewState ->
        val name = poolSharedStateProvider.requireSelectValidatorsState.requirePoolName
        val poolViewState = TitleValueViewState(resourceManager.getString(R.string.pool_staking_selected_pool), name)
        val selectedValidatorsViewState = TitleValueViewState(
            resourceManager.getString(R.string.staking_confirm_selected_validators),
            poolSharedStateProvider.requireSelectValidatorsState.selectedValidators.size.toString()
        )
        listOf(addressViewState, poolViewState, selectedValidatorsViewState, feeViewState)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onNavigationClick() {
        router.back()
    }
}
