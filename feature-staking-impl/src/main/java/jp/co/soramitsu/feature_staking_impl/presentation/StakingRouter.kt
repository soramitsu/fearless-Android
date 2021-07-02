package jp.co.soramitsu.feature_staking_impl.presentation

import androidx.lifecycle.Lifecycle
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.model.PendingPayoutParcelable
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.confirm.ConfirmBondMorePayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm.ConfirmSetControllerPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingStoryModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm.ConfirmRebondPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.confirm.ConfirmUnbondPayload
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel

interface StakingRouter {

    fun openSetupStaking()

    fun openStartChangeValidators()

    fun openRecommendedValidators()

    fun openSelectCustomValidators()

    fun openCustomValidatorsSettings()

    fun openSearchCustomValidators()

    fun openReviewCustomValidators()

    fun openValidatorDetails(validatorDetails: ValidatorDetailsParcelModel)

    fun openConfirmStaking()

    fun openConfirmNominations()

    fun returnToMain()

    fun openChangeAccountFromStaking()

    fun openStory(story: StakingStoryModel)

    fun openPayouts()

    fun openPayoutDetails(payout: PendingPayoutParcelable)

    fun openConfirmPayout(payload: ConfirmPayoutPayload)

    fun openStakingBalance()

    fun openBondMore(payload: SelectBondMorePayload)

    fun openConfirmBondMore(payload: ConfirmBondMorePayload)

    fun returnToStakingBalance()

    fun openSelectUnbond()

    fun openConfirmUnbond(payload: ConfirmUnbondPayload)

    fun openRedeem(payload: RedeemPayload)

    fun openConfirmRebond(payload: ConfirmRebondPayload)

    fun openControllerAccount()

    fun back()

    fun openConfirmSetController(payload: ConfirmSetControllerPayload)

    fun openCustomRebond()

    fun openCurrentValidators()

    fun returnToCurrentValidators()

    fun openChangeRewardDestination()

    fun openConfirmRewardDestination(payload: ConfirmRewardDestinationPayload)

    val currentStackEntryLifecycle: Lifecycle
}
