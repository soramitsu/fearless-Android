package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SelectedValidatorsViewModel @Inject constructor(
    private val poolSharedStateProvider: StakingPoolSharedStateProvider,
    private val poolInteractor: StakingPoolInteractor,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter
) : BaseViewModel(), SelectedValidatorsInterface {

    private val validatorsToShow: List<AccountId> = poolSharedStateProvider.requireSelectedValidatorsState.selectedValidators
    private val canChangeValidators: Boolean = poolSharedStateProvider.requireSelectedValidatorsState.requireCanChangeValidators
    private val chain: Chain = poolSharedStateProvider.requireMainState.requireChain
    private val asset: Asset = poolSharedStateProvider.requireMainState.requireAsset

    private val validatorsFlow = flowOf { poolInteractor.getValidators(chain, validatorsToShow) }

    val state: StateFlow<LoadingState<SelectedValidatorsScreenViewState>> = validatorsFlow.map { validators ->
        val items = validators.map { it.toModel(true, BlockProducersSorting.ValidatorSorting.APYSorting, asset, resourceManager) }
        val listState = MultiSelectListViewState(items, items)

        LoadingState.Loaded(SelectedValidatorsScreenViewState(listState, canChangeValidators))
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        LoadingState.Loading()
    )

    override fun onBackClick() {
        router.back()
    }

    override fun onChangeValidatorsClick() {
        val poolId = poolSharedStateProvider.requireSelectedValidatorsState.requirePoolId
        val poolName = poolSharedStateProvider.requireSelectedValidatorsState.requirePoolName
        poolSharedStateProvider.selectValidatorsState.set(
            SelectValidatorFlowState(
                selectedValidators = validatorsToShow,
                poolName = poolName,
                poolId = poolId
            )
        )
        router.openStartSelectValidators()
    }

    override fun onInfoClick(item: SelectableListItemState<String>) {
        viewModelScope.launch {
            val validator = validatorsFlow.first().find { it.accountIdHex == item.id }
            router.openValidatorDetails(mapValidatorToValidatorDetailsParcelModel(requireNotNull(validator)))
        }
    }
}
