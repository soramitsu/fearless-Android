package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.common.list.toValueList
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.NominatedValidator
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.mappers.mapValidatorToValidatorDetailsWithStakeFlagParcelModel
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SelectedValidatorsViewModel @Inject constructor(
    private val poolSharedStateProvider: StakingPoolSharedStateProvider,
    private val poolInteractor: StakingPoolInteractor,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter
) : BaseViewModel(), SelectedValidatorsInterface {

    private val validatorsToShow: List<AccountId> = poolSharedStateProvider.requireSelectedValidatorsState.selectedValidators
    private val poolId: BigInteger = poolSharedStateProvider.requireSelectedValidatorsState.requirePoolId
    private val canChangeValidators: Boolean = poolSharedStateProvider.requireSelectedValidatorsState.requireCanChangeValidators
    private val chain: Chain = poolSharedStateProvider.requireMainState.requireChain
    private val asset: Asset = poolSharedStateProvider.requireMainState.requireAsset
    private val accountId = poolSharedStateProvider.requireMainState.accountId

    private val validatorsFlow: MutableStateFlow<GroupedList<NominatedValidator.Status.Group, NominatedValidator>?> = MutableStateFlow(null)

    private val flattenCurrentValidators = validatorsFlow
        .map { it?.toValueList() }
        .share()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            poolInteractor.nominatedValidatorsFlow(chain, poolId)
                .collect { groupedValidators ->
                    validatorsFlow.value = groupedValidators
                }
        }
    }

    private val currentValidatorModels = validatorsFlow.map { groupedList ->
        val hasActive = groupedList?.keys?.any { it is NominatedValidator.Status.Group.Active } == true

        groupedList?.map { (statusGroup, validators) ->
            val validatorsViewStates = validators.map {
                it.validator.toModel(true, BlockProducersSorting.ValidatorSorting.APYSorting, asset, resourceManager)
            }
            val listState = MultiSelectListViewState(validatorsViewStates, validatorsViewStates)
            mapNominatedValidatorStatusToViewState(statusGroup, listState, hasActive)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<LoadingState<SelectedValidatorsScreenViewState>> = currentValidatorModels.map { groups ->
        groups ?: return@map LoadingState.Loading()

        LoadingState.Loaded(SelectedValidatorsScreenViewState(groups, canChangeValidators))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loading())

    override fun onBackClick() {
        router.back()
    }

    override fun onChangeValidatorsClick() {
        val poolName = poolSharedStateProvider.requireSelectedValidatorsState.requirePoolName
        poolSharedStateProvider.selectValidatorsState.set(
            SelectValidatorFlowState(selectedValidators = validatorsToShow, poolName = poolName, poolId = poolId)
        )
        router.openStartSelectValidators()
    }

    override fun onInfoClick(item: SelectableListItemState<String>) {
        viewModelScope.launch {
            val payload = withContext(Dispatchers.Default) {
                val allValidators = flattenCurrentValidators.first()

                val nominatedValidator = allValidators?.first { it.validator.accountIdHex == item.id }

                mapValidatorToValidatorDetailsWithStakeFlagParcelModel(requireNotNull(nominatedValidator))
            }

            router.openValidatorDetails(payload)
        }
    }

    private fun mapNominatedValidatorStatusToViewState(
        statusGroup: NominatedValidator.Status.Group,
        validators: MultiSelectListViewState<String>,
        hasActive: Boolean
    ): GroupViewState {
        return when (statusGroup) {
            is NominatedValidator.Status.Group.Active -> GroupViewState(
                title = resourceManager.getString(
                    R.string.staking_your_elected_format,
                    statusGroup.numberOfValidators
                ),
                titleIcon = R.drawable.ic_status_success_16,
                description = resourceManager.getString(R.string.staking_your_allocated_description),
                listState = validators
            )
            is NominatedValidator.Status.Group.Inactive -> GroupViewState(
                title = resourceManager.getString(
                    R.string.staking_your_not_elected_format,
                    statusGroup.numberOfValidators
                ),
                description = resourceManager.getString(R.string.staking_your_inactive_description),
                listState = validators
            )
            is NominatedValidator.Status.Group.Elected -> GroupViewState(
                title = if (!hasActive) resourceManager.getString(R.string.staking_your_elected_format, statusGroup.numberOfValidators) else null,
                titleIcon = if (!hasActive) R.drawable.ic_status_success_16 else null,
                description = resourceManager.getString(R.string.staking_your_not_allocated_description),
                listState = validators
            )
            is NominatedValidator.Status.Group.WaitingForNextEra -> GroupViewState(
                title = resourceManager.getString(
                    R.string.staking_custom_header_validators_title,
                    statusGroup.numberOfValidators,
                    statusGroup.maxValidatorsPerNominator
                ),
                titleIcon = R.drawable.ic_time_24,
                description = resourceManager.getString(R.string.staking_your_validators_changing_title),
                listState = validators
            )
        }
    }
}
