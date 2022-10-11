package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AssetSelectorState
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.StakingStoryModel
import jp.co.soramitsu.common.presentation.StoryElement
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.childScope
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.staking.impl.domain.getSelectedChain
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.common.StakingAssetSelector
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.presentation.staking.balance.manageStakingActionValidationFailure
import jp.co.soramitsu.staking.impl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.EstimatedEarningsViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakeInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakingAssetInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.default
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.update
import jp.co.soramitsu.staking.impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.BaseStakingViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingScenario
import jp.co.soramitsu.staking.impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val CURRENT_ICON_SIZE = 40

@HiltViewModel
class StakingViewModel @Inject constructor(
    private val interactor: StakingInteractor,
    alertsInteractor: AlertsInteractor,
    stakingViewStateFactory: StakingViewStateFactory,
    private val router: StakingRouter,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    @Named("StakingChainUpdateSystem") stakingUpdateSystem: UpdateSystem,
    private val stakingSharedState: StakingSharedState,
    parachainScenarioInteractor: StakingParachainScenarioInteractor,
    relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val setupStakingSharedState: SetupStakingSharedState,
    stakingPoolInteractor: StakingPoolInteractor,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider
) : BaseViewModel(),
    BaseStakingViewModel,
    Validatable by validationExecutor {

    override val stakingStateScope: CoroutineScope
        get() = viewModelScope.childScope(supervised = true)

    private val stakingScenario = StakingScenario(
        stakingSharedState,
        this,
        interactor,
        parachainScenarioInteractor,
        relayChainScenarioInteractor,
        rewardCalculatorFactory,
        resourceManager,
        alertsInteractor,
        stakingViewStateFactory,
        stakingPoolInteractor
    )

    val assetSelectorMixin = StakingAssetSelector(stakingSharedState, this)

    val stakingTypeFlow = stakingSharedState.selectionItem.map { it.type }

    private val _showRewardEstimationEvent = MutableLiveData<Event<StakingRewardEstimationBottomSheet.Payload>>()
    val showRewardEstimationEvent: LiveData<Event<StakingRewardEstimationBottomSheet.Payload>> = _showRewardEstimationEvent

    private val scenarioViewModelFlow = stakingSharedState.selectionItem
        .debounce(50)
        .onEach {
            stakingStateScope.coroutineContext.cancelChildren()
        }
        .map { stakingScenario.getViewModel(it.type) }
        .shareIn(stakingStateScope, started = SharingStarted.Eagerly, replay = 1)

    val networkInfo = scenarioViewModelFlow
        .flatMapLatest {
            it.networkInfo()
        }.distinctUntilChanged().shareIn(stakingStateScope, started = SharingStarted.Eagerly, replay = 1)

    @Deprecated("Use stakingViewState flow with ready models for compose")
    val stakingViewStateOld = scenarioViewModelFlow
        .flatMapLatest {
            it.getStakingViewStateFlowOld().withLoading()
        }.distinctUntilChanged().shareIn(stakingStateScope, started = SharingStarted.Eagerly, replay = 1)

    val alertsFlow = scenarioViewModelFlow
        .flatMapLatest {
            it.alerts()
        }.distinctUntilChanged().shareIn(stakingStateScope, started = SharingStarted.Eagerly, replay = 1)

    private val defaultNetworkInfoStates = mapOf(
        StakingType.POOL to StakingAssetInfoViewState.StakingPool.default(resourceManager),
        StakingType.RELAYCHAIN to StakingAssetInfoViewState.RelayChain.default(resourceManager),
        StakingType.PARACHAIN to StakingAssetInfoViewState.Parachain.default(resourceManager)
    )

    private val stakingViewState: SharedFlow<StakingViewState?> = scenarioViewModelFlow
        .flatMapLatest {
            it.getStakingViewStateFlow()
        }.distinctUntilChanged().stateIn(scope = stakingStateScope, started = SharingStarted.Eagerly, initialValue = null)

    inline fun <reified T : StakingAssetInfoViewState> Map<StakingType, StakingAssetInfoViewState>.get(type: StakingType): T = get(type) as T

    private val networkInfoState: Flow<StakingAssetInfoViewState?> = networkInfo.map { networkInfoState ->
        val selection = stakingSharedState.selectionItem.first()
        if (selection.type != StakingType.POOL) return@map null // todo it's a stub
        if (networkInfoState is LoadingState.Loaded) {
            when (val state = networkInfoState.data) {
                is StakingNetworkInfoModel.Parachain -> defaultNetworkInfoStates.get<StakingAssetInfoViewState.Parachain>(StakingType.PARACHAIN).update(state)
                is StakingNetworkInfoModel.Pool -> defaultNetworkInfoStates.get<StakingAssetInfoViewState.StakingPool>(StakingType.POOL).update(state)
                is StakingNetworkInfoModel.RelayChain -> defaultNetworkInfoStates.get<StakingAssetInfoViewState.RelayChain>(StakingType.RELAYCHAIN)
                    .update(state)
            }
        } else {
            defaultNetworkInfoStates[selection.type]!!
        }
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = null)

    val state = combine(
        stakingSharedState.selectionItem,
        assetSelectorMixin.selectedAssetModelFlow,
        networkInfoState,
        stakingViewState
    ) { selection, selectedAsset, networkInfo, stakingState ->

        val selectorState = AssetSelectorState(
            selectedAsset.chainName,
            selectedAsset.imageUrl,
            selectedAsset.assetBalance,
            (selectedAsset.selectionItem as? StakingAssetSelection.Pool)?.type?.name
        )

        StakingScreenViewState(
            selectorState,
            if (selection.type !== StakingType.POOL) null else networkInfo,
            if (selection.type !== StakingType.POOL) null else stakingState
        )
    }.debounce(50).stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = null)

    init {
        stakingUpdateSystem.start()
            .launchIn(this)
        viewModelScope.launch {
            stakingSharedState.selectionItem.distinctUntilChanged().collect {
                setupStakingSharedState.set(SetupStakingProcess.Initial(it.type))
                stakingStateScope.coroutineContext.cancelChildren()
            }
        }
    }

    private val selectedChain = interactor.selectedChainFlow()
        .share()

    val stories = interactor.stakingStoriesFlow()
        .map { it.map(::transformStories) }
        .asLiveData()

    val currentAddressModelLiveData = currentAddressModelFlow().asLiveData()

    val networkInfoTitle = selectedChain
        .map { it.name }
        .share()

    fun storyClicked(group: StoryGroupModel) {
        if (group.stories.isNotEmpty()) {
            router.openStory(group)
        }
    }

    fun avatarClicked() {
        router.openChangeAccountFromStaking()
    }

    override fun openCurrentValidators() {
        router.openCurrentValidators()
    }

    override fun openChangeValidators() {
        setupStakingSharedState.set(SetupStakingProcess.SelectBlockProducersStep.Validators(SetupStakingProcess.SelectBlockProducersStep.Payload.ExistingStash))
        router.openStartChangeValidators()
    }

    override fun bondMoreAlertClicked() {
        stakingStateScope.launch {
            val vm = scenarioViewModelFlow.first()
            val validation = vm.getBondMoreValidationSystem()
            requireValidManageStakingAction(validation) {
                val bondMorePayload = SelectBondMorePayload(overrideFinishAction = StakingRouter::returnToMain, collatorAddress = null)

                router.openBondMore(bondMorePayload)
            }
        }
    }

    override fun redeemAlertClicked() {
        stakingStateScope.launch {
            val vm = scenarioViewModelFlow.first()
            val validation = vm.getRedeemValidationSystem()
            requireValidManageStakingAction(validation) {
                val redeemPayload = RedeemPayload(overrideFinishAction = StakingRouter::back, collatorAddress = null)

                router.openRedeem(redeemPayload)
            }
        }
    }

    private suspend fun requireValidManageStakingAction(
        validationSystem: ManageStakingValidationSystem,
        action: () -> Unit
    ) {
        val viewModel = stakingScenario.viewModel.first()

        val stakingState = viewModel.stakingStateFlow.first()
        val stashState = stakingState as? StakingState.Stash ?: return

        validationExecutor.requireValid(
            validationSystem,
            ManageStakingValidationPayload(stashState),
            validationFailureTransformer = { manageStakingActionValidationFailure(it, resourceManager) }
        ) {
            action()
        }
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountProjectionFlow().map {
            interactor.getWalletAddressModel(CURRENT_ICON_SIZE)
        }
    }

    fun onStakingBalance(model: DelegatorViewState.CollatorDelegationModel) {
        openStakingBalance(model.collatorAddress)
    }

    override fun openStakingBalance(collatorAddress: String) {
        router.openStakingBalance(collatorAddress)
    }

    fun openCollatorInfo(model: DelegatorViewState.CollatorDelegationModel) {
        viewModelScope.launch {
            val stakingState = stakingViewStateOld.filterIsInstance<LoadingState.Loaded<DelegatorViewState>>().first()
            (stakingState as? LoadingState.Loaded)?.data?.openCollatorInfo(model)
        }
    }

    fun onEstimatedEarningsInfoClick() {
        launch {
            val chainId = interactor.getSelectedChain().id
            val rewardCalculator = rewardCalculatorFactory.createManual(chainId)

            val maxAPY = rewardCalculator.calculateMaxAPY(chainId)
            val avgAPY = rewardCalculator.calculateAvgAPY()

            val payload = StakingRewardEstimationBottomSheet.Payload(
                maxAPY.formatAsPercentage(),
                avgAPY.formatAsPercentage(),
                R.string.staking_reward_info_apr_max,
                R.string.staking_reward_info_apr_avg
            )

            _showRewardEstimationEvent.value = Event(payload)
        }
    }

    fun onPoolsAmountInput(amount: String) {
        viewModelScope.launch {
            scenarioViewModelFlow.first().enteredAmountFlow.emit(amount.replace(',', '.'))
        }
    }

    private suspend fun prepareStakingPoolState() {
        val asset = stakingSharedState.currentAssetFlow().first()
        val (chain, chainAsset) = stakingSharedState.assetWithChain.first()
        val meta = interactor.getCurrentMetaAccount()
        val address = requireNotNull(meta.address(chain))
        val amount = scenarioViewModelFlow.first().enteredAmountFlow.value
        val amountDecimal = amount.toBigDecimalOrNull().orZero()

        stakingPoolSharedStateProvider.mainState.mutate {
            StakingPoolState(asset = asset, chain = chain, chainAsset = chainAsset, address = address, amount = amountDecimal)
        }
    }

    fun startStakingPoolClick() {
        viewModelScope.launch {
            prepareStakingPoolState()
            router.openStakingPoolWelcome()
        }
    }

    private fun transformStories(story: StoryGroup.Staking): StakingStoryModel = with(story) {
        val elements = elements.map { StoryElement.Staking(it.titleRes, it.bodyRes, it.url) }
        StakingStoryModel(titleRes, iconSymbol, elements)
    }

    fun onManagePoolStake() {
        viewModelScope.launch {
            prepareStakingPoolState()
            router.openManagePoolStake()
        }
    }
}

data class StakingScreenViewState(
    val selectorState: AssetSelectorState,
    val networkInfoState: StakingAssetInfoViewState?, // todo shouldn't be nullable - it's just a stub
    val stakingViewState: StakingViewState?
)

sealed class StakingViewState {
    sealed class Pool : StakingViewState() {
        data class Welcome(val estimatedEarnings: EstimatedEarningsViewState) : Pool()
        data class PoolMember(val stakeInfoViewState: StakeInfoViewState) : Pool()
    }
}
