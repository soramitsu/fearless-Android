package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.UnbondingModel
import jp.co.soramitsu.feature_wallet_api.presentation.model.mapAmountToAmountModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal

class StakingBalanceViewModel(
    private val router: StakingRouter,
    private val redeemValidationSystem: ManageStakingValidationSystem,
    private val unbondValidationSystem: ManageStakingValidationSystem,
    private val bondMoreValidationSystem: ManageStakingValidationSystem,
    private val validationExecutor: ValidationExecutor,
    private val resourceManager: ResourceManager,
    private val interactor: StakingInteractor,
) : BaseViewModel(), Validatable by validationExecutor {

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val stakingBalanceModelLiveData = assetFlow.map { asset ->
        StakingBalanceModel(
            bonded = mapAmountToAmountModel(asset.bonded, asset),
            unbonding = mapAmountToAmountModel(asset.unbonding, asset),
            redeemable = mapAmountToAmountModel(asset.redeemable, asset)
        )
    }
        .inBackground()
        .asLiveData()

    val redeemEnabledLiveData = assetFlow
        .map { it.redeemable > BigDecimal.ZERO }
        .asLiveData()

    val unbondingsLiveData = interactor.currentUnbondingsFlow()
        .combine(assetFlow) { unbondings, asset ->
            unbondings.map { unbonding ->
                val daysLeft = unbonding.daysLeft

                UnbondingModel(
                    daysLeft = resourceManager.getQuantityString(R.plurals.staking_payouts_days_left, daysLeft, daysLeft),
                    amountModel = mapAmountToAmountModel(unbonding.amount, asset)
                )
            }
        }
        .inBackground()
        .asLiveData()

    fun bondMoreClicked() = requireValidManageAction(bondMoreValidationSystem) {
        router.openBondMore()
    }

    fun unbondClicked() = requireValidManageAction(unbondValidationSystem) {
        router.openSelectUnbond()
    }

    fun redeemClicked() = requireValidManageAction(redeemValidationSystem) {
        showMessage("Ready to open REDEEM")
    }

    fun backClicked() {
        router.back()
    }

    fun unbondingsMoreClicked() {
        showMessage("Ready to show REBOND actions")
    }

    private fun requireValidManageAction(
        validationSystem: ManageStakingValidationSystem,
        block: (ManageStakingValidationPayload) -> Unit,
    ) {
        launch {
            val stakingState = interactor.selectedAccountStakingStateFlow().first()
            require(stakingState is StakingState.Stash)

            validationExecutor.requireValid(
                validationSystem,
                ManageStakingValidationPayload(stakingState),
                validationFailureTransformer = ::manageStakingActionValidationFailure,
                block = block
            )
        }
    }

    private fun manageStakingActionValidationFailure(reason: ManageStakingValidationFailure): TitleAndMessage {
        return when (reason) {
            is ManageStakingValidationFailure.ControllerRequired -> {
                resourceManager.getString(R.string.common_error_general_title) to
                    resourceManager.getString(R.string.staking_no_controller_account, reason.controllerAddress)
            }

            ManageStakingValidationFailure.ElectionPeriodOpen -> {
                resourceManager.getString(R.string.staking_nominator_status_election) to
                    resourceManager.getString(R.string.staking_nominator_status_alert_election_message)
            }

            is ManageStakingValidationFailure.UnbondingRequestLimitReached -> {
                resourceManager.getString(R.string.staking_unbonding_limit_reached_title) to
                    resourceManager.getString(R.string.staking_unbonding_limit_reached_message, reason.limit)
            }
            is ManageStakingValidationFailure.StashRequired -> {
                resourceManager.getString(R.string.common_error_general_title) to
                    resourceManager.getString(R.string.staking_no_stash_account, reason.stashAddress)
            }
        }
    }
}
