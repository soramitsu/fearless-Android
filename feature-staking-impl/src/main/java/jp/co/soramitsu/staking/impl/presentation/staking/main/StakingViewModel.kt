package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AssetSelectorState
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
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
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
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
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
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
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingScenarioViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.presentation.model.ControllerDeprecationWarningModel
import jp.co.soramitsu.wallet.impl.presentation.model.toModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.transformLatest
import jp.co.soramitsu.staking.impl.domain.model.StakeSummarySource
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Named
import jp.co.soramitsu.core.models.Asset as ChainAsset

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

    // Root/Fragment subscriptions live with the ViewModel. Scenario children use a separate owner.
    override val stakingStateScope: CoroutineScope
        get() = viewModelScope

    private var scenarioScope: CoroutineScope? = null

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

    val assetSelectorMixin = StakingAssetSelector(stakingSharedState, this, resourceManager.getString(jp.co.soramitsu.common.R.string.portfolio_balance_unavailable))

    val stakingTypeFlow = stakingSharedState.selectionItem.map { it.type }

    private val _showRewardEstimationEvent = MutableLiveData<Event<StakingRewardEstimationBottomSheet.Payload>?>()
    val showRewardEstimationEvent: LiveData<Event<StakingRewardEstimationBottomSheet.Payload>?> = _showRewardEstimationEvent

    private val _enteredAmountEvent = MutableSharedFlow<Event<BigDecimal>>()
    override val enteredAmountEvent: Flow<Event<BigDecimal>> = _enteredAmountEvent

    private val _showManageStakeEvent = MutableLiveData<Event<ManageStakingBottomSheet.Payload>?>()
    val showManageStakeEvent: LiveData<Event<ManageStakingBottomSheet.Payload>?> = _showManageStakeEvent
    private class ManagementPresentation(val payload: ManageStakingBottomSheet.Payload, val request: RootActionRequest)
    private var managementPresentation: ManagementPresentation? = null

    private val _showStakingStatusEvent = MutableLiveData<Event<StakingViewState.StatusInfo>?>()
    val showStakingStatusEvent: LiveData<Event<StakingViewState.StatusInfo>?> = _showStakingStatusEvent

    private val scenarioViewModelFlow = stakingSharedState.selectionItem
        .distinctUntilChanged()
        .map { selection ->
            invalidateStaleManagementPresentation()
            scenarioScope?.cancel()
            val owner = viewModelScope.childScope(supervised = true)
            scenarioScope = owner
            val initial = SetupStakingProcess.Initial(selection.type)
            setupStakingSharedState.set(initial)
            stakingPoolSharedStateProvider.poolsCache.update { emptyMap() }
            stakingScenario.createViewModel(selection.type, owner).also {
                it.enteredAmountFlow.value = initial.defaultAmount
            }
        }
        .shareIn(viewModelScope, started = SharingStarted.Eagerly, replay = 1)

    val networkInfo = scenarioViewModelFlow
        .flatMapLatest {
            it.networkInfo()
        }.distinctUntilChanged().shareIn(viewModelScope, started = SharingStarted.Eagerly, replay = 1)

    @Deprecated("Use stakingViewState flow with ready models for compose")
    val stakingViewStateOld = scenarioViewModelFlow
        .flatMapLatest {
            it.stakingViewStateFlowOld.withLoading()
        }.distinctUntilChanged().shareIn(viewModelScope, started = SharingStarted.Lazily, replay = 1)

    private var alertPublication: Any? = null
    private val alertPublicationRevision = MutableStateFlow(Any())

    val alertsFlow = scenarioViewModelFlow
        .flatMapLatest { scenario ->
            scenario.alerts().map { scenario to it }
        }.combine(alertPublicationRevision) { (scenario, status), _ ->
            publishAlerts(scenario, status)
        }.shareIn(viewModelScope, started = SharingStarted.Eagerly, replay = 1)

    private fun publishAlerts(
        scenario: StakingScenarioViewModel,
        status: LoadingState<List<AlertModel>>
    ): LoadingState<List<AlertModel>> {
        val publication = Any().also { alertPublication = it }
        if (status !is LoadingState.Loaded) return LoadingState.Loading()
        return LoadingState.Loaded(status.data.map { model ->
            val action = model.type as? AlertModel.Type.CallToAction ?: return@map model
            val sourceIsCurrent = {
                alertPublication === publication && model.sourceIsCurrent() &&
                    scenarioViewModelFlow.replayCache.lastOrNull() === scenario
            }
            val owner = captureRootAction(sourceIsCurrent)
            AlertModel(model.icon, model.title, model.extraMessage, AlertModel.Type.CallToAction { callerIsCurrent ->
                if (owner != null && isRootActionCurrent(owner) && callerIsCurrent()) {
                    action.invokeIfCurrent { sourceIsCurrent() && callerIsCurrent() }
                }
            }, model.sourceIsCurrent)
        })
    }

    override suspend fun captureAlertSource(state: StakingState, type: StakingType): () -> Boolean = try {
        captureStakingSourceLease(state, type)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // Unavailable source metadata cannot authorize a body or terminate its root observer.
        { false }
    }

    private suspend fun captureStakingSourceLease(state: StakingState, type: StakingType): () -> Boolean {
        val selection = stakingSharedState.selectionRevision.capture()
        val wallet = WalletSelectionEpoch.changes
            .map { runCatching { WalletSelectionEpoch.snapshot() }.getOrNull() }
            .filterNotNull().first()
        val isCurrent = {
            WalletSelectionEpoch.isCurrent(wallet) &&
                stakingSharedState.selectionRevision.capture() === selection
        }
        val asset = actionAssetFlow.first()
        if (!isCurrent()) return { false }
        val config = asset.token.configuration
        if (!supportsStakingAction(config, type) || state.chain.id != config.chainId ||
            !stakingSharedState.selectionRevision.matchesAction(selection, type, config.chainId, config.id)) return { false }
        val (chain, chainAsset) = stakingSharedState.assetWithChain.first()
        if (!isCurrent()) return { false }
        val meta = interactor.getCurrentMetaAccount()
        if (!isCurrent() || asset.metaId != meta.id || chain.id != state.chain.id ||
            chainAsset.chainId != config.chainId || chainAsset.id != config.id ||
            !supportsStakingAction(chainAsset, type) || !state.accountId.contentEquals(asset.accountId) ||
            !state.accountId.contentEquals(meta.accountId(chain))) return { false }
        return isCurrent
    }

    private val defaultNetworkInfoStates = mapOf(
        StakingType.POOL to StakingAssetInfoViewState.StakingPool.default(resourceManager),
        StakingType.RELAYCHAIN to StakingAssetInfoViewState.RelayChain.default(resourceManager),
        StakingType.PARACHAIN to StakingAssetInfoViewState.Parachain.default(resourceManager)
    )

    private class RelayPublication(
        val state: StakingViewState.RelayChain.Stake,
        val wallet: Long,
        val selection: StakingSelectionRevision.Snapshot
    )
    private var relayPublication: RelayPublication? = null
    private class ParachainPublication(
        val state: StakingViewState.Parachain.Delegator,
        val wallet: Long,
        val selection: StakingSelectionRevision.Snapshot
    )
    private var parachainPublication: ParachainPublication? = null
    private val bodyPublicationRevision = MutableStateFlow(Any())

    /** Withdraw action authority before the rendering debounce can retain the previous body. */
    private fun publishStakingBody(
        state: StakingViewState?,
        wallet: Long? = null,
        selection: StakingSelectionRevision.Snapshot? = null
    ): StakingViewState? {
        val published = (state as? StakingViewState.RelayChain.Stake)?.let {
            it.copy(manageActions = it.manageActions.toSet(), publicationId = Any())
        }
        relayPublication = if (published != null && wallet != null && selection != null) {
            RelayPublication(published, wallet, selection)
        } else null
        val parachain = (state as? StakingViewState.Parachain.Delegator)?.let {
            it.copy(delegations = it.delegations.map { model -> model.copy() }, publicationId = Any())
        }
        parachainPublication = if (parachain != null && wallet != null && selection != null) {
            ParachainPublication(parachain, wallet, selection)
        } else null
        clearManagementPresentation()
        _showStakingStatusEvent.value = null
        return published ?: parachain ?: state
    }

    private fun isRelayPublicationCurrent(publication: RelayPublication): Boolean =
        relayPublication === publication && WalletSelectionEpoch.isCurrent(publication.wallet) &&
            stakingSharedState.selectionRevision.capture() === publication.selection

    /** Data from a lagging scenario cannot acquire a newly selected wallet or asset's authority. */
    private suspend fun qualifyStakingBody(state: StakingViewState?): StakingViewState? {
        if (state is StakingViewState.Parachain.Delegator) {
            // The originating scenario supplied its own role/selection lease before body awaits.
            val wallet = runCatching { WalletSelectionEpoch.snapshot() }.getOrNull()
            return publishStakingBody(state, wallet, stakingSharedState.selectionRevision.capture())
        }
        if (state !is StakingViewState.RelayChain.Stake) return publishStakingBody(state)
        val source = state.source ?: return null
        val wallet = runCatching { WalletSelectionEpoch.snapshot() }.getOrNull() ?: return null
        val selection = stakingSharedState.selectionRevision.capture()
        fun isCurrent(): Boolean = WalletSelectionEpoch.isCurrent(wallet) &&
            stakingSharedState.selectionRevision.capture() === selection
        if (!stakingSharedState.selectionRevision.matchesAction(selection, StakingType.RELAYCHAIN, source.chainId, source.assetId)) return null
        val meta = interactor.getCurrentMetaAccount()
        if (!isCurrent() || meta.id != source.metaId) return null
        val asset = actionAssetFlow.first()
        if (!isCurrent() || !source.matches(asset)) return null
        val (chain, chainAsset) = stakingSharedState.assetWithChain.first()
        if (!isCurrent() || chain.id != source.chainId || chainAsset.chainId != source.chainId ||
            chainAsset.id != source.assetId || chainAsset.precision != source.precision ||
            chainAsset.symbol != source.symbol || chainAsset.staking != source.stakingType ||
            source.stakingType != ChainAsset.StakingType.RELAYCHAIN ||
            !asset.accountId.contentEquals(meta.accountId(chain))) return null
        val published = publishStakingBody(state, wallet, selection)
        return if (isCurrent()) published else publishStakingBody(null)
    }

    private val stakingViewState: SharedFlow<StakingViewState?> = scenarioViewModelFlow
        .flatMapLatest {
            it.getStakingViewStateFlow()
        }.distinctUntilChanged()
        .combine(bodyPublicationRevision) { body, _ -> body }
        .combine(WalletSelectionEpoch.changes) { body, _ -> body }
        .combine(stakingSharedState.selectionRevision.changes) { body, _ -> body }
        .transformLatest { body ->
            // Fail closed before metadata suspension; a replacement cancels this admission.
            emit(publishStakingBody(null))
            try {
                emit(qualifyStakingBody(body))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emit(publishStakingBody(null))
            }
        }.stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = null)

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
        combine(WalletSelectionEpoch.changes, stakingSharedState.selectionRevision.changes) { _, _ -> Unit }
            .onEach { invalidateStaleManagementPresentation() }
            .launchIn(viewModelScope)

        stakingUpdateSystem.start()
            .launchIn(this)

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
        .flatMapLatest { viewModel ->
            viewModel.stakingStoriesFlow().map { it.map(::transformStories) }
                .onStart { emit(emptyList()) }
        }.distinctUntilChanged().shareIn(viewModelScope, started = SharingStarted.Eagerly, replay = 1)

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
        onStakingScreenPaused()
        router.openSelectWallet()
    }

    override fun openCurrentValidators(isCurrent: () -> Boolean) {
        val request = captureRootAction(isCurrent) ?: return
        navigateFromRoot(request) { router.openCurrentValidators() }
    }

    override fun openChangeValidators(isCurrent: () -> Boolean) {
        val request = captureRootAction(isCurrent) ?: return
        navigateFromRoot(request) {
            setupStakingSharedState.set(SetupStakingProcess.SelectBlockProducersStep.Validators(SetupStakingProcess.SelectBlockProducersStep.Payload.ExistingStash))
            router.openStartChangeValidators()
        }
    }

    override fun bondMoreAlertClicked(isCurrent: () -> Boolean) {
        val request = captureRootAction(isCurrent) ?: return
        launchRootNavigation(request) { action ->
            val (scenario, _) = currentScenarioAction(action) ?: return@launchRootNavigation
            val validation = scenario.getBondMoreValidationSystem()
            requireValidManageStakingAction(action, scenario, validation) {
                val payload = SelectBondMorePayload(overrideFinishAction = StakingRouter::returnToMain, collatorAddress = null)
                router.openBondMore(payload)
            }
        }
    }

    override fun redeemAlertClicked(isCurrent: () -> Boolean) {
        val request = captureRootAction(isCurrent) ?: return
        launchRootNavigation(request) { action ->
            val (scenario, _) = currentScenarioAction(action) ?: return@launchRootNavigation
            val validation = scenario.getRedeemValidationSystem()
            requireValidManageStakingAction(action, scenario, validation) {
                val payload = RedeemPayload(overrideFinishAction = StakingRouter::back, collatorAddress = null)
                router.openRedeem(payload)
            }
        }
    }

    private suspend fun requireValidManageStakingAction(
        request: RootActionRequest,
        scenario: StakingScenarioViewModel,
        validationSystem: ManageStakingValidationSystem,
        action: () -> Unit
    ) {
        if (!isRootActionCurrent(request)) return
        val stakingState = scenario.stakingStateFlow.first()
        if (!isRootActionCurrent(request)) return
        val stashState = stakingState as? StakingState.Stash ?: return
        validationExecutor.requireValid(
            validationSystem,
            ManageStakingValidationPayload(stashState),
            errorDisplayer = { error -> publishRootAction(request) { showError(error) } },
            validationFailureTransformer = { manageStakingActionValidationFailure(it, resourceManager) },
            isCurrent = { isRootActionCurrent(request) }
        ) {
            navigateFromRoot(request, action)
        }
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountProjectionFlow().map {
            interactor.getWalletAddressModel(CURRENT_ICON_SIZE)
        }
    }

    private fun captureParachainAction(
        model: CollatorStakeInfoViewState,
        isScreenCurrent: () -> Boolean
    ): RootActionRequest? {
        val publication = parachainPublication ?: return null
        if (publication.state.delegations.none { it === model }) return null
        return captureRootAction(isScreenCurrent = {
            isScreenCurrent() && parachainPublication === publication && publication.state.sourceIsCurrent() &&
                WalletSelectionEpoch.isCurrent(publication.wallet) &&
                stakingSharedState.selectionRevision.capture() === publication.selection
        })
    }

    fun onStakingBalance(model: CollatorStakeInfoViewState, isScreenCurrent: () -> Boolean = { true }) {
        val request = captureParachainAction(model, isScreenCurrent) ?: return
        navigateFromRoot(request) { router.openStakingBalance(model.collatorAddress) }
    }

    override fun openStakingBalance(collatorAddress: String, isCurrent: () -> Boolean) {
        val request = captureRootAction(isCurrent) ?: return
        navigateFromRoot(request) { router.openStakingBalance(collatorAddress) }
    }

    fun openCollatorInfo(model: CollatorStakeInfoViewState, isScreenCurrent: () -> Boolean = { true }) {
        val request = captureParachainAction(model, isScreenCurrent) ?: return
        val scenario = scenarioViewModelFlow.replayCache.lastOrNull() as? StakingParachainScenarioViewModel ?: return
        launchRootNavigation(request) {
            val details = scenario.prepareCollatorInfo(model) {
                isRootActionCurrent(request) && scenarioViewModelFlow.replayCache.lastOrNull() === scenario
            } ?: return@launchRootNavigation
            navigateFromRoot(request) { router.openCollatorDetails(details) }
        }
    }

    fun onEstimatedEarningsInfoClick() = onEstimatedEarningsInfoClick { true }

    fun onEstimatedEarningsInfoClick(isScreenCurrent: () -> Boolean) {
        if (rewardRequest?.let(::isRootActionCurrent) == true) return
        val request = captureRootAction(isScreenCurrent) ?: return
        rewardRequest?.active = false
        rewardJob?.cancel()
        rewardRequest = request
        rewardJob = viewModelScope.launch {
            try {
                if (!isRootActionCurrent(request)) return@launch
                val (scenario, asset) = currentScenarioAction(request) ?: return@launch
                val payload = scenario.rewardEstimationPayload(asset)
                publishRootAction(request) { _showRewardEstimationEvent.value = Event(payload) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                publishRootAction(request) { showError(error) }
            } finally {
                if (rewardRequest === request) {
                    rewardRequest = null
                    rewardJob = null
                }
            }
        }
    }

    fun onPoolsAmountInput(amount: BigDecimal?) {
        viewModelScope.launch {
            scenarioViewModelFlow.first().enteredAmountFlow.emit(amount)
        }
    }

    private suspend fun prepareStakingPoolState(
        request: StakingPoolSharedStateProvider.PoolPreparation,
        action: RootActionRequest
    ): Boolean {
        if (!isRootActionCurrent(action) || !stakingPoolSharedStateProvider.isPoolPreparationCurrent(request)) return false
        val state = jp.co.soramitsu.staking.impl.presentation.common.prepareStakingPoolContext(
            stakingSharedState, interactor, request.walletEpoch, request.selection
        ) { scenarioViewModelFlow.first().enteredAmountFlow.value } ?: return false
        if (!isRootActionCurrent(action)) return false
        return stakingPoolSharedStateProvider.publishPoolPreparation(request, state, stakingSharedState.selectionRevision) &&
            isRootActionCurrent(action)
    }

    private val actionAssetFlow by lazy { stakingSharedState.currentAssetFlow() }

    /** Reject lagging scenario/asset replays before optional metadata or setup work starts. */
    private suspend fun currentScenarioAction(request: RootActionRequest): Pair<StakingScenarioViewModel, Asset>? {
        val scenario = scenarioViewModelFlow.first()
        if (!isRootActionCurrent(request)) return null
        val asset = actionAssetFlow.first()
        if (!isRootActionCurrent(request)) return null
        val config = asset.token.configuration
        val matchesSelection = stakingSharedState.selectionRevision.matchesAction(
            request.selection, scenario.stakingType, config.chainId, config.id
        )
        if (!supportsStakingAction(config, scenario.stakingType) || !matchesSelection) return null
        val (chain, chainAsset) = stakingSharedState.assetWithChain.first()
        if (!isRootActionCurrent(request)) return null
        if (!supportsStakingAction(chainAsset, scenario.stakingType) || chainAsset.chainId != chain.id) return null
        val meta = interactor.getCurrentMetaAccount()
        if (!isRootActionCurrent(request) || asset.metaId != meta.id ||
            config.chainId != chain.id || config.id != chainAsset.id ||
            !asset.accountId.contentEquals(meta.accountId(chain))) return null
        return scenario to asset
    }

    private fun supportsStakingAction(config: ChainAsset, type: StakingType): Boolean = when (type) {
        StakingType.RELAYCHAIN -> config.staking == ChainAsset.StakingType.RELAYCHAIN
        StakingType.PARACHAIN -> config.staking == ChainAsset.StakingType.PARACHAIN
        StakingType.POOL -> config.supportStakingPool
    }

    private class RootActionRequest(
        val screen: Any,
        val wallet: Long,
        val selection: StakingSelectionRevision.Snapshot,
        val isScreenCurrent: () -> Boolean,
        val relay: RelayPublication? = null,
        var active: Boolean = true
    )

    private var rootScreenGeneration: Any = Any()
    private var rewardRequest: RootActionRequest? = null
    private var rewardJob: Job? = null
    private var navigationRequest: RootActionRequest? = null
    private var navigationJob: Job? = null

    private fun isRootOwnerActive(): Boolean = viewModelScope.coroutineContext[Job]?.isActive == true

    /** Captures ownership at the tap, before any queued or suspended scenario work. */
    private fun captureRootAction(isScreenCurrent: () -> Boolean, relay: RelayPublication? = null): RootActionRequest? {
        if (!recoveryScreenActive || !isScreenCurrent() || !isRootOwnerActive()) return null
        val wallet = runCatching { WalletSelectionEpoch.snapshot() }.getOrNull() ?: return null
        if (relay != null && !isRelayPublicationCurrent(relay)) return null
        return RootActionRequest(rootScreenGeneration, wallet, stakingSharedState.selectionRevision.capture(), isScreenCurrent, relay)
    }

    private fun isRootActionCurrent(request: RootActionRequest): Boolean =
        request.active && recoveryScreenActive && request.screen === rootScreenGeneration && request.isScreenCurrent() && isRootOwnerActive() &&
            WalletSelectionEpoch.isCurrent(request.wallet) && stakingSharedState.selectionRevision.capture() === request.selection &&
            (request.relay == null || isRelayPublicationCurrent(request.relay))

    private fun publishRootAction(request: RootActionRequest, action: () -> Unit): Boolean =
        WalletSelectionEpoch.runIfCurrentOrNull(request.wallet) {
            if (!isRootActionCurrent(request)) false else {
                action()
                true
            }
        } == true

    private fun launchRootNavigation(
        request: RootActionRequest,
        action: suspend (RootActionRequest) -> Unit
    ) {
        navigationRequest?.active = false
        navigationJob?.cancel()
        navigationRequest = request
        navigationJob = viewModelScope.launch {
            try {
                if (isRootActionCurrent(request)) action(request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                publishRootAction(request) { showError(error) }
            } finally {
                if (navigationRequest === request) {
                    // Keep identity until the next action/pause also revokes deferred validation callbacks.
                    navigationJob = null
                }
            }
        }
    }

    /** Consume the originating screen before router callbacks can synchronously reenter it. */
    private fun navigateFromRoot(request: RootActionRequest, navigate: () -> Unit) {
        publishRootAction(request) {
            val departure = Any()
            pauseStakingScreen(departure)
            if (rootScreenGeneration !== departure || recoveryScreenActive || !request.isScreenCurrent() || !isRootOwnerActive() ||
                !WalletSelectionEpoch.isCurrent(request.wallet) ||
                stakingSharedState.selectionRevision.capture() !== request.selection ||
                (request.relay != null && !isRelayPublicationCurrent(request.relay))) {
                resumeAbortedDeparture(request, departure)
                return@publishRootAction
            }
            try {
                navigate()
            } catch (error: CancellationException) {
                resumeAbortedDeparture(request, departure)
                throw error
            } catch (error: Exception) {
                // The view can accept a fresh action after an aborted departure, even if its
                // wallet/selection changed. Errors still belong to the original context.
                if (resumeAbortedDeparture(request, departure) && WalletSelectionEpoch.isCurrent(request.wallet) &&
                    stakingSharedState.selectionRevision.capture() === request.selection) {
                    showError(error)
                }
            }
        }
    }

    private fun resumeAbortedDeparture(request: RootActionRequest, departure: Any): Boolean {
        if (rootScreenGeneration !== departure || recoveryScreenActive || !request.isScreenCurrent() || !isRootOwnerActive()) return false
        onStakingScreenResumed()
        return true
    }

    private var recoveryScreenActive = true
    private var recoveryPreparationGeneration: Any = Any()
    private var recoveryPreparationJob: kotlinx.coroutines.Job? = null

    /** Retained/tab-hidden screens cannot publish a late recovery route or metadata error. */
    fun onStakingScreenPaused() = pauseStakingScreen(Any())

    private fun pauseStakingScreen(generation: Any) {
        recoveryScreenActive = false
        rootScreenGeneration = generation
        rewardRequest?.active = false
        rewardRequest = null
        rewardJob?.cancel()
        rewardJob = null
        navigationRequest?.active = false
        navigationRequest = null
        navigationJob?.cancel()
        navigationJob = null
        _showRewardEstimationEvent.value = null
        _showManageStakeEvent.value = null
        _showStakingStatusEvent.value = null
        clearManagementPresentation()
        recoveryPreparationGeneration = Any()
        recoveryPreparationJob?.cancel()
        recoveryPreparationJob = null
    }

    fun onStakingScreenResumed() {
        recoveryScreenActive = true
        // Re-render a fresh publication; callbacks retained across pause never gain new authority.
        relayPublication = null
        parachainPublication = null
        bodyPublicationRevision.value = Any()
        alertPublicationRevision.value = Any()
    }

    /** Local recovery does not wait for fresh staking eligibility, fees or scenario metadata. */
    fun onPoolJoinRecovery(isScreenCurrent: () -> Boolean = { true }) {
        if (!recoveryScreenActive || !isScreenCurrent()) return
        recoveryPreparationJob?.cancel()
        val generation = Any().also { recoveryPreparationGeneration = it }
        val request = stakingPoolSharedStateProvider.beginPoolPreparation(stakingSharedState.selectionRevision.capture()) ?: return
        recoveryPreparationJob = viewModelScope.launch {
            if (generation !== recoveryPreparationGeneration || !recoveryScreenActive || !isScreenCurrent() ||
                !stakingPoolSharedStateProvider.isPoolPreparationCurrent(request)) return@launch
            try {
                val state = jp.co.soramitsu.staking.impl.presentation.common.prepareStakingPoolContext(
                    stakingSharedState, interactor, request.walletEpoch, request.selection
                ) { null } ?: return@launch
                if (generation === recoveryPreparationGeneration && recoveryScreenActive && isScreenCurrent() &&
                    stakingPoolSharedStateProvider.publishPoolPreparation(request, state, stakingSharedState.selectionRevision) &&
                    generation === recoveryPreparationGeneration && recoveryScreenActive && isScreenCurrent()) {
                    router.openPoolJoinRecovery()
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation === recoveryPreparationGeneration && recoveryScreenActive && isScreenCurrent() &&
                    stakingPoolSharedStateProvider.isPoolPreparationCurrent(request) &&
                    stakingSharedState.selectionRevision.capture() === request.selection) {
                    showError(resourceManager.getString(R.string.pool_join_recovery_unavailable))
                }
            }
        }
    }

    fun startStakingPoolClick() = startStakingPoolClick { true }

    fun startStakingPoolClick(isScreenCurrent: () -> Boolean) {
        val action = captureRootAction(isScreenCurrent) ?: return
        val request = stakingPoolSharedStateProvider.beginPoolPreparation(action.selection) ?: return
        launchRootNavigation(action) {
            if (prepareStakingPoolState(request, it)) navigateFromRoot(it) { router.openStakingPoolWelcome() }
        }
    }

    fun startNonPoolStakingClick() = startNonPoolStakingClick { true }

    fun startNonPoolStakingClick(isScreenCurrent: () -> Boolean) {
        val request = captureRootAction(isScreenCurrent) ?: return
        launchRootNavigation(request) { action ->
            val (scenario, asset) = currentScenarioAction(action) ?: return@launchRootNavigation
            if (scenario.stakingType == StakingType.POOL) return@launchRootNavigation
            scenario.startStaking(
                asset = asset,
                isCurrent = { isRootActionCurrent(action) },
                onReady = { process ->
                    publishRootAction(action) {
                        setupStakingSharedState.set(process)
                        // Setup observers can replace the owner synchronously. Recheck before departure.
                        navigateFromRoot(action) { router.openSetupStaking() }
                    }
                }
            )
        }
    }

    fun onManageRelayChainStake(state: StakingViewState.RelayChain.Stake) = onManageRelayChainStake(state) { true }

    /** A displayed action belongs to this exact screen and selection, not a later dialog. */
    fun onManageRelayChainStake(state: StakingViewState.RelayChain.Stake, isScreenCurrent: () -> Boolean) {
        val publication = relayPublication?.takeIf { it.state === state } ?: return
        val request = captureRootAction(isScreenCurrent, publication) ?: return
        val payload = ManageStakingBottomSheet.Payload(state.manageActions.toSet())
        managementPresentation?.request?.active = false
        managementPresentation = ManagementPresentation(payload, request)
        publishRootAction(request) { _showManageStakeEvent.value = Event(payload) }
    }

    /** Fragment callbacks must carry the exact payload that created their dialog. */
    fun onManageStakeActionChosen(payload: ManageStakingBottomSheet.Payload, action: ManageStakeAction) {
        val presentation = managementPresentation ?: return
        if (presentation.payload !== payload || action !in payload.availableActions || !isRootActionCurrent(presentation.request)) return
        managementPresentation = null

        navigateFromRoot(presentation.request) {
            when (action) {
                ManageStakeAction.PAYOUTS -> router.openPayouts()
                ManageStakeAction.BALANCE -> router.openStakingBalance()
                ManageStakeAction.CONTROLLER -> router.openControllerAccount()
                ManageStakeAction.VALIDATORS -> router.openCurrentValidators()
                ManageStakeAction.REWARD_DESTINATION -> router.openChangeRewardDestination()
            }
        }
    }

    /** Recheck after native dialog creation/dismissal, whose callbacks may change ownership. */
    fun isManagementPresentationCurrent(payload: ManageStakingBottomSheet.Payload): Boolean =
        managementPresentation?.let { it.payload === payload && isRootActionCurrent(it.request) } == true

    /** A late dismiss callback from A cannot invalidate replacement B. */
    fun onManagementPresentationDismissed(payload: ManageStakingBottomSheet.Payload) {
        if (managementPresentation?.payload === payload) clearManagementPresentation()
    }

    private fun invalidateStaleManagementPresentation() {
        if (managementPresentation?.let { !isRootActionCurrent(it.request) } == true) clearManagementPresentation()
    }

    private fun clearManagementPresentation() {
        managementPresentation?.request?.active = false
        managementPresentation = null
        _showManageStakeEvent.value = null
    }

    fun onRelayChainStatusClick(state: StakingViewState.RelayChain.Stake) = onRelayChainStatusClick(state) { true }

    fun onRelayChainStatusClick(state: StakingViewState.RelayChain.Stake, isScreenCurrent: () -> Boolean) {
        val publication = relayPublication?.takeIf { it.state === state } ?: return
        val request = captureRootAction(isScreenCurrent, publication) ?: return
        publishRootAction(request) { _showStakingStatusEvent.value = Event(state.statusInfo) }
    }

    private fun transformStories(story: StoryGroup.Staking): StakingStoryModel = with(story) {
        val elements = elements.map { StoryElement.Staking(it.titleRes, it.bodyRes, it.url) }
        StakingStoryModel(titleRes, iconSymbol, elements)
    }

    fun onManagePoolStake() = onManagePoolStake { true }

    fun onManagePoolStake(isScreenCurrent: () -> Boolean) {
        val action = captureRootAction(isScreenCurrent) ?: return
        val request = stakingPoolSharedStateProvider.beginPoolPreparation(action.selection) ?: return
        launchRootNavigation(action) {
            if (prepareStakingPoolState(request, it)) navigateFromRoot(it) { router.openManagePoolStake() }
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
            val statusInfo: StatusInfo,
            // A null→equal recovery must render a new owner even if the debounce omits null.
            internal val publicationId: Any? = null,
            internal val source: StakeSummarySource? = null
        ) : RelayChain()
    }

    sealed class Parachain : StakingViewState() {
        data class Welcome(val estimatedEarnings: EstimatedEarningsViewState) : Parachain()

        data class Delegator(
            val delegations: List<CollatorStakeInfoViewState>,
            val estimatedEarnings: EstimatedEarningsViewState,
            internal val sourceIsCurrent: () -> Boolean = { false },
            internal val publicationId: Any? = null
        ) : Parachain()
    }

    data class StatusInfo(val title: String, val message: String)
}
