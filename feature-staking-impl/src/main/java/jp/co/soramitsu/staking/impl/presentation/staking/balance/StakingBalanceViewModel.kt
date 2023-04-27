package jp.co.soramitsu.staking.impl.presentation.staking.balance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.model.Unbonding
import jp.co.soramitsu.staking.impl.domain.staking.unbond.UnbondInteractor
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.balance.StakingBalanceFragment.Companion.KEY_COLLATOR_ADDRESS
import jp.co.soramitsu.staking.impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.staking.impl.presentation.staking.balance.model.UnbondingModel
import jp.co.soramitsu.staking.impl.presentation.staking.balance.rebond.RebondKind
import jp.co.soramitsu.staking.impl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm.ConfirmRebondPayload
import jp.co.soramitsu.staking.impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.select.SelectUnbondPayload
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.model.mapAmountToAmountModel
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

@HiltViewModel
class StakingBalanceViewModel @Inject constructor(
    private val router: StakingRouter,
    private val validationExecutor: ValidationExecutor,
    private val unbondingInteractor: UnbondInteractor,
    private val resourceManager: ResourceManager,
    interactor: StakingInteractor,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), Validatable by validationExecutor {

    private val collatorAddress = savedStateHandle.get<String?>(KEY_COLLATOR_ADDRESS)

    private val refresh = MutableStateFlow(Event(Unit))

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val stakingBalanceModelLiveData: Flow<StakingBalanceModel> = refresh.flatMapLatest {
        stakingScenarioInteractor.getStakingBalanceFlow(collatorAddress?.fromHex())
    }.share()

    private val unbondingsFlow: Flow<List<Unbonding>> = refresh.flatMapLatest {
        stakingScenarioInteractor.currentUnbondingsFlow(collatorAddress)
    }

    val redeemTitle = stakingScenarioInteractor.overrideRedeemActionTitle()

    val redeemEnabledLiveData = stakingBalanceModelLiveData.map {
        it.redeemable.amount > BigDecimal.ZERO
    }.asLiveData()

    val pendingAction = MutableLiveData(false)

    val shouldBlockStakeMore = stakingBalanceModelLiveData.map {
        val isParachain = assetFlow.first().token.configuration.staking == Asset.StakingType.PARACHAIN
        val isUnstakingFullAmount = (it.staked.amount - it.unstaking.amount).compareTo(BigDecimal.ZERO) == 0
        val stakeIsZero = it.staked.amount.compareTo(BigDecimal.ZERO) == 0
        val isFullUnstake = isUnstakingFullAmount || stakeIsZero

        isFullUnstake.and(isParachain)
    }.onStart { emit(true) }.asLiveData()

    val shouldBlockUnstake = stakingBalanceModelLiveData.map {
        val asset = assetFlow.first()
        val isParachain = asset.token.configuration.staking == Asset.StakingType.PARACHAIN
        val stakedAmountIsZero = asset.token.planksFromAmount(it.staked.amount) == BigInteger.ZERO
        if (stakedAmountIsZero) {
            return@map true
        } else {
            val hasPendingUnstake = it.redeemable.amount + it.unstaking.amount > BigDecimal.ZERO
            hasPendingUnstake.and(isParachain)
        }
    }.onStart { emit(true) }.asLiveData()

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

    val unbondingEnabledLiveData = combine(unbondingsFlow, refresh) { _, _ ->
        stakingScenarioInteractor.getRebondingUnbondings(collatorAddress).isNotEmpty()
    }.onStart { emit(false) }.share().asLiveData()

    private val _showRebondActionsEvent = MutableLiveData<Event<Set<RebondKind>>>()
    val showRebondActionsEvent: LiveData<Event<Set<RebondKind>>> = _showRebondActionsEvent

    fun bondMoreClicked() = requireValidManageAction(stakingScenarioInteractor.getBondMoreValidation()) {
        pendingAction.value = false
        router.openBondMore(
            SelectBondMorePayload(
                overrideFinishAction = null,
                collatorAddress = collatorAddress,
                oneScreenConfirmation = collatorAddress != null
            )
        )
    }

    fun unbondClicked() = requireValidManageAction(stakingScenarioInteractor.getUnbondingValidation()) {
        pendingAction.value = false
        router.openSelectUnbond(
            SelectUnbondPayload(
                collatorAddress = collatorAddress,
                oneScreenConfirmation = collatorAddress != null
            )
        )
    }

    fun redeemClicked() = requireValidManageAction(stakingScenarioInteractor.getRedeemValidation()) {
        pendingAction.value = false
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
        requireValidManageAction(stakingScenarioInteractor.getRebondValidation()) {
            pendingAction.value = false
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
        block: (ManageStakingValidationPayload) -> Unit
    ) {
        launch {
            pendingAction.value = true
            val stakingState = stakingScenarioInteractor.getSelectedAccountStakingState()
            validationExecutor.requireValid(
                validationSystem,
                ManageStakingValidationPayload(stakingState as? StakingState.Stash),
                progressConsumer = {
                    if (it.not()) {
                        pendingAction.value = false
                    }
                },
                validationFailureTransformer = { manageStakingActionValidationFailure(it, resourceManager) },
                block = block
            )
        }
    }

    fun refresh() {
        refresh.tryEmit(Event(Unit))
    }
}
