package jp.co.soramitsu.featurestakingimpl.presentation

import androidx.lifecycle.Lifecycle
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.featurestakingimpl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.featurestakingimpl.presentation.payouts.model.PendingPayoutParcelable
import jp.co.soramitsu.featurestakingimpl.presentation.staking.bond.confirm.ConfirmBondMorePayload
import jp.co.soramitsu.featurestakingimpl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.featurestakingimpl.presentation.staking.controller.confirm.ConfirmSetControllerPayload
import jp.co.soramitsu.featurestakingimpl.presentation.staking.rebond.confirm.ConfirmRebondPayload
import jp.co.soramitsu.featurestakingimpl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.featurestakingimpl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload
import jp.co.soramitsu.featurestakingimpl.presentation.staking.unbond.confirm.ConfirmUnbondPayload
import jp.co.soramitsu.featurestakingimpl.presentation.staking.unbond.select.SelectUnbondPayload
import jp.co.soramitsu.featurestakingimpl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.featurestakingimpl.presentation.validators.parcel.ValidatorDetailsParcelModel

interface StakingRouter {

    fun openSetupStaking()

    fun openStartChangeCollators()

    fun openRecommendedCollators()

    fun openSelectCustomCollators()

    fun openStartChangeValidators()

    fun openRecommendedValidators()

    fun openSelectCustomValidators()

    fun openCustomValidatorsSettingsFromValidator()

    fun openCustomValidatorsSettingsFromCollator()

    fun openSearchCustomValidators()

    fun openSearchCustomCollators()

    fun openReviewCustomValidators()

    fun openValidatorDetails(validatorDetails: ValidatorDetailsParcelModel)

    fun openCollatorDetails(collatorDetails: CollatorDetailsParcelModel)

    fun openConfirmStaking()

    fun openConfirmNominations()

    fun returnToMain()

    fun openChangeAccountFromStaking()

    fun openStory(story: StoryGroupModel)

    fun openPayouts()

    fun openPayoutDetails(payout: PendingPayoutParcelable)

    fun openConfirmPayout(payload: ConfirmPayoutPayload)

    fun openStakingBalance(collatorAddress: String? = null)

    fun openBondMore(payload: SelectBondMorePayload)

    fun openConfirmBondMore(payload: ConfirmBondMorePayload)

    fun returnToStakingBalance()

    fun openSelectUnbond(payload: SelectUnbondPayload)

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
