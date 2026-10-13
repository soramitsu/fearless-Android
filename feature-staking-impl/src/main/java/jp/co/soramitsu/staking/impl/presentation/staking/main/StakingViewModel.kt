package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AssetSelectorState
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.StakingStoryModel
import jp.co.soramitsu.common.presentation.StoryElement
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.childScope
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.repository.datasource.ParachainStakingStoriesDataSourceImpl
import jp.co.soramitsu.staking.impl.data.repository.datasource.StakingStoriesDataSourceImpl
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.rewards.SoraStakingRewardsScenario
import jp.co.soramitsu.staking.impl.domain.setup.SetupStakingInteractor
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
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.CollatorStakeInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakeInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakingAssetInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.default
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.update
import jp.co.soramitsu.staking.impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.BaseStakingViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingParachainScenarioViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingScenario
import jp.co.soramitsu.staking.impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.presentation.model.ControllerDeprecationWarningModel
import jp.co.soramitsu.wallet.impl.presentation.model.toModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Named

private const val CURRENT_ICON_SIZE = 40

internal fun mapStakingNetworkInfoState(
    stakingType: StakingType,
    networkInfoState: LoadingState<StakingNetworkInfoModel>,
    defaultStates: Map<StakingType, StakingAssetInfoViewState>
): StakingAssetInfoViewState {
    val defaultState = requireNotNull(defaultStates[stakingType]) {
        "Missing default network info state for $stakingType"
    }
    val loadedState = (networkInfoState as? LoadingState.Loaded)?.data

    return when (stakingType) {
        StakingType.POOL -> {
            require(defaultState is StakingAssetInfoViewState.StakingPool) {
                "Invalid default network info state for $stakingType"
            }
            (loadedState as? StakingNetworkInfoModel.Pool)?.let(defaultState::update) ?: defaultState
        }
        StakingType.RELAYCHAIN -> {
            require(defaultState is StakingAssetInfoViewState.RelayChain) {
                "Invalid default network info state for $stakingType"
            }
            (loadedState as? StakingNetworkInfoModel.RelayChain)?.let(defaultState::update) ?: defaultState
        }
        StakingType.PARACHAIN -> {
            require(defaultState is StakingAssetInfoViewState.Parachain) {
                "Invalid default network info state for $stakingType"
            }
            (loadedState as? StakingNetworkInfoModel.Parachain)?.let(defaultState::update) ?: defaultState
        }
    }
}

internal fun mapStakingBodyState(
    stakingType: StakingType,
    state: StakingViewState?
): StakingViewState? {
    return when (stakingType) {
        StakingType.POOL -> state as? StakingViewState.Pool
        StakingType.RELAYCHAIN -> state as? StakingViewState.RelayChain
        StakingType.PARACHAIN -> state as? StakingViewState.Parachain
    }
}

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
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingParachainStoriesDataSourceImpl: ParachainStakingStoriesDataSourceImpl,
    private val stakingStoriesDataSourceImpl: StakingStoriesDataSourceImpl,
    private val setupStakingInteractor: SetupStakingInteractor,
    private val quickInputsUseCase: QuickInputsUseCase,
    private val soraStakingRewardsScenario: SoraStakingRewardsScenario
) : BaseViewModel(),
    BaseStakingViewModel,
    Validatable by validationExecutor {

    val isInputFocused = MutableStateFlow(false)

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
        stakingPoolInteractor,
        stakingParachainStoriesDataSourceImpl,
        stakingStoriesDataSourceImpl,
        setupStakingSharedState,
        router,
        validationExecutor,
        soraStakingRewardsScenario
    )

    val assetSelectorMixin = StakingAssetSelector(stakingSharedState, this)

    val stakingTypeFlow = stakingSharedState.selectionItem.map { it.type }

    private val _showRewardEstimationEvent = MutableLiveData<Event<StakingRewardEstimationBottomSheet.Payload>>()
    val showRewardEstimationEvent: LiveData<Event<StakingRewardEstimationBottomSheet.Payload>> = _showRewardEstimationEvent

    private val _enteredAmountEvent = MutableSharedFlow<Event<BigDecimal>>()
    override val enteredAmountEvent: Flow<Event<BigDecimal>> = _enteredAmountEvent

    private val _showManageStakeEvent = MutableLiveData<Event<ManageStakingBottomSheet.Payload>>()
    val showManageStakeEvent: LiveData<Event<ManageStakingBottomSheet.Payload>> = _showManageStakeEvent
    private var availableManageStakeActions: Set<ManageStakeAction> = emptySet()

    private val _showStakingStatusEvent = MutableLiveData<Event<StakingViewState.StatusInfo>>()
    val showStakingStatusEvent: LiveData<Event<StakingViewState.StatusInfo>> = _showStakingStatusEvent

    private val scenarioViewModelFlow = stakingSharedState.selectionItem
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
            it.stakingViewStateFlowOld.withLoading()
        }.distinctUntilChanged().shareIn(stakingStateScope, started = SharingStarted.Lazily, replay = 1)

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

    private val networkInfoState: Flow<StakingAssetInfoViewState> = combine(
        stakingSharedState.selectionItem,
        networkInfo
    ) { selection, state ->
        mapStakingNetworkInfoState(selection.type, state, defaultNetworkInfoStates)
    }

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
            networkInfo,
            mapStakingBodyState(selection.type, stakingState)
        )
    }.debounce(50).stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = null)

    private val quickInputsStateFlow = MutableStateFlow<Map<Double, BigDecimal>?>(null)
    init {
        stakingUpdateSystem.start()
            .launchIn(this)

        stakingSharedState.selectionItem.distinctUntilChanged().onEach {
            availableManageStakeActions = emptySet()
            val initial = SetupStakingProcess.Initial(it.type)
            setupStakingSharedState.set(initial)
            stakingScenario.getViewModel(it.type).enteredAmountFlow.value = initial.defaultAmount
            stakingStateScope.coroutineContext.cancelChildren()

            stakingPoolSharedStateProvider.poolsCache.update { emptyMap() }
        }.launchIn(viewModelScope)

        interactor.selectionStateFlow().onEach {
            val warning = interactor.checkControllerDeprecations(it.first, it.second.chain)
            warning?.let {
                val model = warning.toModel(resourceManager)
                showError(
                    title = model.title,
                    message = model.message,
                    positiveButtonText = model.buttonText,
                    negativeButtonText = null,
                    positiveClick = {
                        when (model.action) {
                            ControllerDeprecationWarningModel.Action.ChangeController -> {
                                router.openManageControllerAccount(model.chainId)
                            }
                            ControllerDeprecationWarningModel.Action.ImportStash -> {
                                router.openImportAccountScreenFromWallet(0)
                            }
                        }
                    }
                )
            }
        }.launchIn(viewModelScope)

        combine(
            stakingSharedState.selectionItem,
            stakingSharedState.currentAssetFlow()
        ) { selectionItem, asset ->
            val quickInputs = quickInputsUseCase.calculateStakingQuickInputs(
                selectionItem.chainId,
                selectionItem.chainAssetId,
                calculateAvailableAmount = {
                    asset.availableForStaking
                },
                calculateFee = {
                    when (selectionItem.type) {
                        StakingType.PARACHAIN -> {
                            interactor.getSelectedAccountProjection()?.address?.let { address ->
                                setupStakingInteractor.estimateParachainFee(address)
                            }.orZero()
                        }
                        else -> {
                            interactor.getSelectedAccountProjection()?.address?.let { address ->
                                setupStakingInteractor.estimateMaxSetupStakingFee(address)
                            }.orZero()
                        }
                    }
                })
            quickInputsStateFlow.update { quickInputs }
        }.launchIn(viewModelScope)
    }

    private val selectedChain = interactor.selectedChainFlow()
        .share()

    val stories = scenarioViewModelFlow
        .flatMapConcat { viewModel ->
            viewModel.stakingStoriesFlow().map { it.map(::transformStories) }
        }.distinctUntilChanged().shareIn(stakingStateScope, started = SharingStarted.Eagerly, replay = 1)

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
        router.openSelectWallet()
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

    fun onStakingBalance(model: CollatorStakeInfoViewState) {
        openStakingBalance(model.collatorAddress)
    }

    override fun openStakingBalance(collatorAddress: String) {
        router.openStakingBalance(collatorAddress)
    }

    fun openCollatorInfo(model: CollatorStakeInfoViewState) {
        val scenario = stakingScenario.getViewModel(StakingType.PARACHAIN) as StakingParachainScenarioViewModel
        scenario.openCollatorInfo(model)
    }

    fun onEstimatedEarningsInfoClick() {
        launch {
            _showRewardEstimationEvent.value = Event(scenarioViewModelFlow.first().rewardEstimationPayload())
        }
    }

    fun onPoolsAmountInput(amount: BigDecimal?) {
        viewModelScope.launch {
            scenarioViewModelFlow.first().enteredAmountFlow.emit(amount)
        }
    }

    private suspend fun prepareStakingPoolState() {
        val asset = stakingSharedState.currentAssetFlow().first()
        val (chain, chainAsset) = stakingSharedState.assetWithChain.first()
        val meta = interactor.getCurrentMetaAccount()
        val address = requireNotNull(meta.address(chain))
        val amount = scenarioViewModelFlow.first().enteredAmountFlow.value

        stakingPoolSharedStateProvider.mainState.mutate {
            StakingPoolState(asset = asset, chain = chain, chainAsset = chainAsset, address = address, amount = amount)
        }
    }

    fun startStakingPoolClick() {
        viewModelScope.launch {
            prepareStakingPoolState()
            router.openStakingPoolWelcome()
        }
    }

    fun startNonPoolStakingClick() {
        viewModelScope.launch {
            scenarioViewModelFlow.first().startStaking()
        }
    }

    fun onManageRelayChainStake(state: StakingViewState.RelayChain.Stake) {
        availableManageStakeActions = state.manageActions
        _showManageStakeEvent.value = Event(ManageStakingBottomSheet.Payload(state.manageActions))
    }

    fun onManageStakeActionChosen(action: ManageStakeAction) {
        if (action !in availableManageStakeActions) return
        availableManageStakeActions = emptySet()

        when (action) {
            ManageStakeAction.PAYOUTS -> router.openPayouts()
            ManageStakeAction.BALANCE -> router.openStakingBalance()
            ManageStakeAction.CONTROLLER -> router.openControllerAccount()
            ManageStakeAction.VALIDATORS -> router.openCurrentValidators()
            ManageStakeAction.REWARD_DESTINATION -> router.openChangeRewardDestination()
        }
    }

    fun onRelayChainStatusClick(state: StakingViewState.RelayChain.Stake) {
        _showStakingStatusEvent.value = Event(state.statusInfo)
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

    fun onAmountInputFocusChanged(hasFocus: Boolean) {
        launch {
            isInputFocused.emit(hasFocus)
        }
    }

    fun onQuickAmountInput(input: Double) {
        launch {
            val valuesMap = quickInputsStateFlow.first { !it.isNullOrEmpty() }.cast<Map<Double, BigDecimal>>()
            val amount = valuesMap[input] ?: return@launch
            _enteredAmountEvent.emit(Event(amount))
        }
    }
}

data class StakingScreenViewState(
    val selectorState: AssetSelectorState,
    val networkInfoState: StakingAssetInfoViewState,
    val stakingViewState: StakingViewState?
)

sealed class StakingViewState {
    sealed class Pool : StakingViewState() {
        data class Welcome(val estimatedEarnings: EstimatedEarningsViewState) : Pool()
        data class PoolMember(val stakeInfoViewState: StakeInfoViewState) : Pool()
    }

    sealed class RelayChain : StakingViewState() {
        data class Welcome(val estimatedEarnings: EstimatedEarningsViewState) : RelayChain()

        data class Stake(
            val stakeInfoViewState: StakeInfoViewState.RelayChainStakeInfoViewState,
            val manageActions: Set<ManageStakeAction>,
            val statusInfo: StatusInfo
        ) : RelayChain()
    }

    sealed class Parachain : StakingViewState() {
        data class Welcome(val estimatedEarnings: EstimatedEarningsViewState) : Parachain()

        data class Delegator(
            val delegations: List<CollatorStakeInfoViewState>,
            val estimatedEarnings: EstimatedEarningsViewState
        ) : Parachain()
    }

    data class StatusInfo(val title: String, val message: String)
}
