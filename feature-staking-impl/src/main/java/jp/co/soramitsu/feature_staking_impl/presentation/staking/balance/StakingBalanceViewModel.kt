package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.Unbonding
import jp.co.soramitsu.feature_staking_impl.domain.staking.unbond.UnbondInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.UnbondingModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.rebond.RebondKind
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm.ConfirmRebondPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.select.SelectUnbondPayload
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.model.mapAmountToAmountModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger

class StakingBalanceViewModel(
    private val router: StakingRouter,
    private val redeemValidationSystem: ManageStakingValidationSystem,
    private val unbondValidationSystem: ManageStakingValidationSystem,
    private val bondMoreValidationSystem: ManageStakingValidationSystem,
    private val rebondValidationSystem: ManageStakingValidationSystem,
    private val validationExecutor: ValidationExecutor,
    private val unbondingInteractor: UnbondInteractor,
    private val resourceManager: ResourceManager,
    interactor: StakingInteractor,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val collatorAddress: String?
) : BaseViewModel(), Validatable by validationExecutor {

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val stakingBalanceModelLiveData: LiveData<StakingBalanceModel> = flowOf {}.flatMapLatest {
        stakingScenarioInteractor.getStakingBalanceFlow(collatorAddress?.fromHex())
    }.asLiveData()

    private val unbondingsFlow: Flow<List<Unbonding>> = flowOf {}.flatMapLatest {
        stakingScenarioInteractor.currentUnbondingsFlow(collatorAddress)
    }

    val redeemTitle = stakingScenarioInteractor.overrideRedeemActionTitle()

    val redeemEnabledLiveData = stakingBalanceModelLiveData.map {
        it.redeemable.amount > BigDecimal.ZERO
    }

    val hasScheduledRequestsLiveData = stakingBalanceModelLiveData.map {
        it.redeemable.amount + it.unstaking.amount > BigDecimal.ZERO
    }

    val unbondingModelsLiveData = unbondingsFlow
        .combine(assetFlow) { unbondings, asset ->
            unbondings.mapIndexed { index, unbonding ->

                UnbondingModel(
                    index = index,
                    timeLeft = unbonding.timeLeft,
                    calculatedAt = unbonding.calculatedAt,
                    amountModel = mapAmountToAmountModel(unbonding.amount, asset, unbonding.type?.nameResId)
                )
            }
        }
        .inBackground()
        .asLiveData()

    val unbondingEnabledLiveData = flowOf {
        stakingScenarioInteractor.getRebondingUnbondings(collatorAddress).isNotEmpty()
    }.onStart { emit(false) }.share().asLiveData()

    private val _showRebondActionsEvent = MutableLiveData<Event<Set<RebondKind>>>()
    val showRebondActionsEvent: LiveData<Event<Set<RebondKind>>> = _showRebondActionsEvent

    fun bondMoreClicked() = requireValidManageAction(bondMoreValidationSystem) {
        router.openBondMore(
            SelectBondMorePayload(
                overrideFinishAction = null,
                collatorAddress = collatorAddress,
                oneScreenConfirmation = collatorAddress != null
            )
        )
    }

    fun unbondClicked() = requireValidManageAction(unbondValidationSystem) {
        router.openSelectUnbond(
            SelectUnbondPayload(
                collatorAddress = collatorAddress,
                oneScreenConfirmation = collatorAddress != null
            )
        )
    }

    fun redeemClicked() = requireValidManageAction(redeemValidationSystem) {
        router.openRedeem(
            RedeemPayload(
                overrideFinishAction = null,
                collatorAddress = collatorAddress
            )
        )
    }

    fun backClicked() {
        router.back()
    }

    fun unbondingsMoreClicked() {
        val allowedRebondTypes = stakingScenarioInteractor.getRebondTypes()
        requireValidManageAction(rebondValidationSystem) {
            _showRebondActionsEvent.postValue(Event(allowedRebondTypes))
        }
    }

    fun rebondKindChosen(rebondKind: RebondKind) {
        when (rebondKind) {
            RebondKind.LAST -> openConfirmRebond(unbondingInteractor::newestUnbondingAmount)
            RebondKind.ALL -> openConfirmRebond(unbondingInteractor::allUnbondingsAmount)
            RebondKind.CUSTOM -> router.openCustomRebond()
        }
    }

    private fun openConfirmRebond(amountBuilder: (List<Unbonding>) -> BigInteger) {
        launch {
            val unbondings = stakingScenarioInteractor.getRebondingUnbondings(collatorAddress)

            val amountInPlanks = amountBuilder(unbondings)
            val asset = assetFlow.first()

            val amount = asset.token.amountFromPlanks(amountInPlanks)

            router.openConfirmRebond(ConfirmRebondPayload(amount, collatorAddress))
        }
    }

    private fun requireValidManageAction(
        validationSystem: ManageStakingValidationSystem,
        block: (ManageStakingValidationPayload) -> Unit,
    ) {
        launch {
            val stakingState = stakingScenarioInteractor.getSelectedAccountStakingState()

            validationExecutor.requireValid(
                validationSystem,
                ManageStakingValidationPayload(stakingState as? StakingState.Stash),
                validationFailureTransformer = { manageStakingActionValidationFailure(it, resourceManager) },
                block = block
            )
        }
    }
}
