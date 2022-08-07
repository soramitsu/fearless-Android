package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.inBackground
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
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.feature_wallet_api.presentation.model.mapAmountToAmountModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class StakingBalanceViewModel(
    private val router: StakingRouter,
    private val validationExecutor: ValidationExecutor,
    private val unbondingInteractor: UnbondInteractor,
    private val resourceManager: ResourceManager,
    interactor: StakingInteractor,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val collatorAddress: String?
) : BaseViewModel(), Validatable by validationExecutor {

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

    val shouldBlockActionButtons = stakingBalanceModelLiveData.map {
        val isParachain = assetFlow.first().token.configuration.staking == Chain.Asset.StakingType.PARACHAIN
        (it.redeemable.amount + it.unstaking.amount > BigDecimal.ZERO).and(isParachain)
    }.onStart { emit(true) }.asLiveData()

    val shouldBlockUnstake = stakingBalanceModelLiveData.map {
        val asset = assetFlow.first()
        val isParachain = asset.token.configuration.staking == Chain.Asset.StakingType.PARACHAIN
        if (asset.token.planksFromAmount(it.staked.amount) == BigInteger.ZERO) return@map true else
            (it.redeemable.amount + it.unstaking.amount > BigDecimal.ZERO).and(isParachain)
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

    val unbondingEnabledLiveData = refresh.map {
        stakingScenarioInteractor.getRebondingUnbondings(collatorAddress).isNotEmpty()
    }.onStart { emit(false) }.share().asLiveData()

    private val _showRebondActionsEvent = MutableLiveData<Event<Set<RebondKind>>>()
    val showRebondActionsEvent: LiveData<Event<Set<RebondKind>>> = _showRebondActionsEvent

    fun bondMoreClicked() = requireValidManageAction(stakingScenarioInteractor.getBondMoreValidation()) {
        router.openBondMore(
            SelectBondMorePayload(
                overrideFinishAction = null,
                collatorAddress = collatorAddress,
                oneScreenConfirmation = collatorAddress != null
            )
        )
    }

    fun unbondClicked() = requireValidManageAction(stakingScenarioInteractor.getUnbondingValidation()) {
        router.openSelectUnbond(
            SelectUnbondPayload(
                collatorAddress = collatorAddress,
                oneScreenConfirmation = collatorAddress != null
            )
        )
    }

    fun redeemClicked() = requireValidManageAction(stakingScenarioInteractor.getRedeemValidation()) {
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

    fun refresh() {
        refresh.tryEmit(Event(Unit))
    }
}
