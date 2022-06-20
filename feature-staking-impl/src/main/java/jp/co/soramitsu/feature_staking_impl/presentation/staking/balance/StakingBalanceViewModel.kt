package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.sendEvent
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
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.model.mapAmountToAmountModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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

    val stakingBalanceModelLiveData = MutableLiveData<StakingBalanceModel>()

    init {
        launch {
            stakingScenarioInteractor.getStakingBalanceFlow(collatorAddress?.fromHex()).onEach {
                stakingBalanceModelLiveData.postValue(it)
            }
                .inBackground()
                .share()
        }
    }

    val redeemTitle = stakingScenarioInteractor.overrideRedeemActionTitle()

    val redeemEnabledLiveData = assetFlow
        .map { it.redeemable > BigDecimal.ZERO }
        .asLiveData()

    private val unbondingsFlow = stakingScenarioInteractor.currentUnbondingsFlow()
        .share()

    val unbondingModelsLiveData = unbondingsFlow
        .combine(assetFlow) { unbondings, asset ->
            unbondings.mapIndexed { index, unbonding ->

                UnbondingModel(
                    index = index,
                    timeLeft = unbonding.timeLeft,
                    calculatedAt = unbonding.calculatedAt,
                    amountModel = mapAmountToAmountModel(unbonding.amount, asset)
                )
            }
        }
        .inBackground()
        .asLiveData()

    private val _showRebondActionsEvent = MutableLiveData<Event<Unit>>()
    val showRebondActionsEvent: LiveData<Event<Unit>> = _showRebondActionsEvent

    fun bondMoreClicked() = requireValidManageAction(bondMoreValidationSystem) {
        router.openBondMore(SelectBondMorePayload(overrideFinishAction = null))
    }

    fun unbondClicked() = requireValidManageAction(unbondValidationSystem) {
        router.openSelectUnbond()
    }

    fun redeemClicked() = requireValidManageAction(redeemValidationSystem) {
        router.openRedeem(RedeemPayload(overrideFinishAction = null))
    }

    fun backClicked() {
        router.back()
    }

    fun unbondingsMoreClicked() {
        requireValidManageAction(rebondValidationSystem) {
            _showRebondActionsEvent.sendEvent()
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
            val unbondings = unbondingsFlow.first()

            val amountInPlanks = amountBuilder(unbondings)
            val asset = assetFlow.first()

            val amount = asset.token.amountFromPlanks(amountInPlanks)

            router.openConfirmRebond(ConfirmRebondPayload(amount))
        }
    }

    private fun requireValidManageAction(
        validationSystem: ManageStakingValidationSystem,
        block: (ManageStakingValidationPayload) -> Unit,
    ) {
        launch {
            val stakingState = stakingScenarioInteractor.getSelectedAccountStakingState()
            require(stakingState is StakingState.Stash)

            validationExecutor.requireValid(
                validationSystem,
                ManageStakingValidationPayload(stakingState),
                validationFailureTransformer = { manageStakingActionValidationFailure(it, resourceManager) },
                block = block
            )
        }
    }
}
