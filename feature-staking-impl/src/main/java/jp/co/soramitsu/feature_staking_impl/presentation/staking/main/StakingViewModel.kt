package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.childScope
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BALANCE_REQUIRED_CONTROLLER
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BALANCE_REQUIRED_STASH
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceAccountRequiredValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.manageStakingActionValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios.BaseStakingViewModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios.StakingScenario
import jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.AssetSelectorMixin
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.WithAssetSelector
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
import javax.inject.Named

private const val CURRENT_ICON_SIZE = 40

class StakingViewModel(
    private val interactor: StakingInteractor,
    alertsInteractor: AlertsInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    stakingViewStateFactory: StakingViewStateFactory,
    private val router: StakingRouter,
    private val resourceManager: ResourceManager,
    @Named(BALANCE_REQUIRED_CONTROLLER)
    controllerRequiredValidation: BalanceAccountRequiredValidation,
    @Named(BALANCE_REQUIRED_STASH)
    stashRequiredValidation: BalanceAccountRequiredValidation,
    private val validationExecutor: ValidationExecutor,
    stakingUpdateSystem: UpdateSystem,
    assetSelectorMixinFactory: AssetSelectorMixin.Presentation.Factory,
    stakingSharedState: StakingSharedState,
    parachainScenarioInteractor: StakingParachainScenarioInteractor,
    relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    rewardCalculatorFactory: RewardCalculatorFactory,
) : BaseViewModel(),
    WithAssetSelector,
    BaseStakingViewModel,
    Validatable by validationExecutor {

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
        controllerRequiredValidation,
        stashRequiredValidation
    )

    override val assetSelectorMixin = assetSelectorMixinFactory.create(scope = this)

    private val scenarioViewModelFlow = assetSelectorMixin.selectedAssetFlow.map { stakingScenario.getViewModel(it.token.configuration.staking) }

    val networkInfo = scenarioViewModelFlow.flatMapLatest {
        it.networkInfo()
    }.distinctUntilChanged().share()

    val stakingViewState = scenarioViewModelFlow.flatMapLatest {
        it.getStakingViewStateFlow()
    }.distinctUntilChanged().share()

    val alertsFlow = scenarioViewModelFlow.flatMapLatest {
        it.alerts()
    }.distinctUntilChanged().share()

    override val stakingStateScope = viewModelScope.childScope(supervised = true)

    init {
        stakingUpdateSystem.start()
            .launchIn(this)
        // todo research
        assetSelectorMixin.selectedAssetModelFlow.onEach {
            stakingStateScope.coroutineContext.cancelChildren()
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
        viewModelScope.launch {
            val validation = stakingScenario.bondMoreValidationSystem.last()
            requireValidManageStakingAction(validation) {
                val bondMorePayload = SelectBondMorePayload(overrideFinishAction = StakingRouter::returnToMain)

                router.openBondMore(bondMorePayload)
            }
        }
    }

    override fun redeemAlertClicked() {
        viewModelScope.launch {
            val validation = stakingScenario.redeemValidationSystem.last()
            requireValidManageStakingAction(validation) {
                val redeemPayload = RedeemPayload(overrideFinishAction = StakingRouter::back)

                router.openRedeem(redeemPayload)
            }
        }
    }

    private fun requireValidManageStakingAction(
        validationSystem: ManageStakingValidationSystem,
        action: () -> Unit,
    ) = launch {
        val stakingState = (stakingScenario.viewModel.map { it.stakingState().first() }.first() as? LoadingState.Loaded)?.data
        val stashState = stakingState as? StakingState.Stash ?: return@launch

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
            addressIconGenerator.createAddressModel(it.address, CURRENT_ICON_SIZE, it.name)
        }
    }

    fun onStakingBalance(model: DelegatorViewState.CollatorDelegationModel) {
        router.openStakingBalance(model.collatorAddress)
    }

    fun openCollatorInfo(model: DelegatorViewState.CollatorDelegationModel) {
    }
}
