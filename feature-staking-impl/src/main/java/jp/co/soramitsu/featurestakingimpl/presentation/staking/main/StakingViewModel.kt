package jp.co.soramitsu.featurestakingimpl.presentation.staking.main

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.childScope
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.featurestakingapi.data.StakingSharedState
import jp.co.soramitsu.featurestakingapi.domain.model.StakingState
import jp.co.soramitsu.featurestakingimpl.domain.StakingInteractor
import jp.co.soramitsu.featurestakingimpl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.featurestakingimpl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.featurestakingimpl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.featurestakingimpl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.featurestakingimpl.presentation.StakingRouter
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.featurestakingimpl.presentation.staking.balance.manageStakingActionValidationFailure
import jp.co.soramitsu.featurestakingimpl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.featurestakingimpl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.featurestakingimpl.presentation.staking.main.scenarios.BaseStakingViewModel
import jp.co.soramitsu.featurestakingimpl.presentation.staking.main.scenarios.StakingScenario
import jp.co.soramitsu.featurestakingimpl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.featurestakingimpl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.featurestakingimpl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.featurewalletapi.presentation.mixin.assetSelector.AssetSelectorMixin
import jp.co.soramitsu.featurewalletapi.presentation.mixin.assetSelector.WithAssetSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
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
    @Named("StakingAssetSelector") assetSelectorMixinFactory: AssetSelectorMixin.Presentation.Factory,
    stakingSharedState: StakingSharedState,
    parachainScenarioInteractor: StakingParachainScenarioInteractor,
    relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    rewardCalculatorFactory: RewardCalculatorFactory,
    private val setupStakingSharedState: SetupStakingSharedState
) : BaseViewModel(),
    WithAssetSelector,
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
        stakingViewStateFactory
    )

    override val assetSelectorMixin = assetSelectorMixinFactory.create(scope = this)

    val stakingTypeFlow = stakingSharedState.assetWithChain.map { interactor.currentAssetFlow().first().token.configuration.staking }

    private val scenarioViewModelFlow = stakingSharedState.assetWithChain.debounce(50).onEach {
        stakingStateScope.coroutineContext.cancelChildren()
    }
        .map {
            val asset = interactor.currentAssetFlow().first()
            stakingScenario.getViewModel(asset.token.configuration.staking)
        }.shareIn(stakingStateScope, started = SharingStarted.Eagerly, replay = 1)

    val networkInfo = scenarioViewModelFlow
        .flatMapLatest {
            it.networkInfo()
        }.distinctUntilChanged().shareIn(stakingStateScope, started = SharingStarted.Eagerly, replay = 1)

    val stakingViewState = scenarioViewModelFlow
        .flatMapLatest {
            it.getStakingViewStateFlow().withLoading()
        }.distinctUntilChanged().shareIn(stakingStateScope, started = SharingStarted.Eagerly, replay = 1)

    val alertsFlow = scenarioViewModelFlow
        .flatMapLatest {
            it.alerts()
        }.distinctUntilChanged().shareIn(stakingStateScope, started = SharingStarted.Eagerly, replay = 1)

    init {
        stakingUpdateSystem.start()
            .launchIn(this)
        viewModelScope.launch {
            stakingSharedState.assetWithChain.distinctUntilChanged().collect {
                setupStakingSharedState.set(SetupStakingProcess.Initial(it.asset.staking))
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
            val stakingState = stakingViewState.filterIsInstance<LoadingState.Loaded<DelegatorViewState>>().first()
            (stakingState as? LoadingState.Loaded)?.data?.openCollatorInfo(model)
        }
    }
}
