package jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.select

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Retriable
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.mappers.mapRewardDestinationModelToRewardDestination
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.staking.rewardDestination.ChangeRewardDestinationInteractor
import jp.co.soramitsu.staking.impl.domain.validations.rewardDestination.RewardDestinationValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.rewardDestination.RewardDestinationValidationSystem
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationMixin
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationModel
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.RewardDestinationParcelModel
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Named

@HiltViewModel
class SelectRewardDestinationViewModel @Inject constructor(
    private val router: StakingRouter,
    interactor: StakingInteractor,
    stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val resourceManager: ResourceManager,
    private val changeRewardDestinationInteractor: ChangeRewardDestinationInteractor,
    private val validationSystem: RewardDestinationValidationSystem,
    private val validationExecutor: ValidationExecutor,
    @Named("StakingFeeLoader") private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val rewardDestinationMixin: RewardDestinationMixin.Presentation
) : BaseViewModel(),
    Retriable,
    Validatable by validationExecutor,
    FeeLoaderMixin by feeLoaderMixin,
    RewardDestinationMixin by rewardDestinationMixin {

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val chainId = interactor.currentAssetFlow().filter { it.token.configuration.staking == Chain.Asset.StakingType.RELAYCHAIN }
        .map { it.token.configuration.chainId }

    private val rewardCalculator = viewModelScope.async { rewardCalculatorFactory.createManual(chainId.first()) }

    private val rewardDestinationFlow = rewardDestinationMixin.rewardDestinationModelFlow
        .map { mapRewardDestinationModelToRewardDestination(it) }
        .share()

    private val stashStateFlow = stakingRelayChainScenarioInteractor.selectedAccountStakingStateFlow()
        .filterIsInstance<StakingState.Stash>()
        .share()

    private val controllerAssetFlow = interactor.currentAssetFlow()
        .share()

    val continueAvailable = rewardDestinationMixin.rewardDestinationChangedFlow
        .asLiveData()

    init {
        rewardDestinationFlow.combine(stashStateFlow) { rewardDestination, stashState ->
            loadFee(rewardDestination, stashState)
        }.launchIn(viewModelScope)

        controllerAssetFlow.onEach {
            rewardDestinationMixin.updateReturns(rewardCalculator(), it, it.bonded)
        }.launchIn(viewModelScope)

        stashStateFlow.onEach(rewardDestinationMixin::loadActiveRewardDestination)
            .launchIn(viewModelScope)
    }

    fun nextClicked() {
        maybeGoToNext()
    }

    fun backClicked() {
        router.back()
    }

    private fun loadFee(rewardDestination: RewardDestination, stashState: StakingState.Stash) {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = { changeRewardDestinationInteractor.estimateFee(stashState, rewardDestination) },
            onRetryCancelled = ::backClicked
        )
    }

    private fun maybeGoToNext() = requireFee { fee ->
        launch {
            val payload = RewardDestinationValidationPayload(
                availableControllerBalance = controllerAssetFlow.first().transferable,
                fee = fee,
                stashState = stashStateFlow.first()
            )

            val rewardDestination = rewardDestinationModelFlow.first()

            validationExecutor.requireValid(
                validationSystem = validationSystem,
                payload = payload,
                validationFailureTransformer = { rewardDestinationValidationFailure(resourceManager, it) },
                progressConsumer = _showNextProgress.progressConsumer()
            ) {
                _showNextProgress.value = false

                goToNextStep(rewardDestination, it.fee)
            }
        }
    }

    private fun goToNextStep(
        rewardDestination: RewardDestinationModel,
        fee: BigDecimal
    ) {
        val payload = ConfirmRewardDestinationPayload(
            fee = fee,
            rewardDestination = mapRewardDestinationModelToRewardDestinationParcelModel(rewardDestination)
        )

        router.openConfirmRewardDestination(payload)
    }

    private fun mapRewardDestinationModelToRewardDestinationParcelModel(rewardDestination: RewardDestinationModel): RewardDestinationParcelModel {
        return when (rewardDestination) {
            RewardDestinationModel.Restake -> RewardDestinationParcelModel.Restake
            is RewardDestinationModel.Payout -> RewardDestinationParcelModel.Payout(rewardDestination.destination.address)
        }
    }

    private fun requireFee(block: (BigDecimal) -> Unit) = feeLoaderMixin.requireFee(
        block,
        onError = { title, message -> showError(title, message) }
    )

    private suspend fun rewardCalculator() = rewardCalculator.await()
}
