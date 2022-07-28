package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.childScope
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.feature_staking_api.data.StakingSharedState
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.manageStakingActionValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios.BaseStakingViewModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios.StakingScenario
import jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.feature_staking_impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.AssetSelectorMixin
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.WithAssetSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val CURRENT_ICON_SIZE = 40

class StakingViewModel(
    private val interactor: StakingInteractor,
    alertsInteractor: AlertsInteractor,
    stakingViewStateFactory: StakingViewStateFactory,
    private val router: StakingRouter,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    stakingUpdateSystem: UpdateSystem,
    assetSelectorMixinFactory: AssetSelectorMixin.Presentation.Factory,
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
        stakingViewStateFactory,
    )

    override val assetSelectorMixin = assetSelectorMixinFactory.create(scope = this)

    private val scenarioViewModelFlow = assetSelectorMixin.selectedAssetFlow
        .map { stakingScenario.getViewModel(it.token.configuration.staking) }

    val networkInfo = scenarioViewModelFlow
        .flatMapLatest {
            it.networkInfo()
        }.distinctUntilChanged().share()

    val stakingViewState = scenarioViewModelFlow
        .flatMapLatest {
            it.getStakingViewStateFlow().withLoading()
        }.distinctUntilChanged().inBackground()
        .onEach { stakingStateScope.coroutineContext.cancelChildren() }

    var alertsFlow = scenarioViewModelFlow
        .flatMapLatest {
            it.alerts()
        }.distinctUntilChanged().share()

    init {
        stakingUpdateSystem.start()
            .launchIn(this)
        // todo research
        viewModelScope.launch {
            assetSelectorMixin.selectedAssetModelFlow.onEach {
                stakingStateScope.coroutineContext.cancelChildren()
            }

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

    override fun bondMoreAlertClicked() {
        stakingStateScope.launch {
            val validation = scenarioViewModelFlow.last().getBondMoreValidationSystem()
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
            hashCode()
            requireValidManageStakingAction(validation) {
                val redeemPayload = RedeemPayload(overrideFinishAction = StakingRouter::back, collatorAddress = null)

                router.openRedeem(redeemPayload)
            }
        }
    }

    private suspend fun requireValidManageStakingAction(
        validationSystem: ManageStakingValidationSystem,
        action: () -> Unit,
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
            val stakingState = stakingViewState.first()
            ((stakingState as? LoadingState.Loaded)?.data as? DelegatorViewState)?.openCollatorInfo(model)
        }
    }

    fun onScreenAppeared() {
        alertsFlow = scenarioViewModelFlow.flatMapLatest {
            it.alerts()
        }.distinctUntilChanged().share()
    }
}
