package jp.co.soramitsu.feature_staking_impl.presentation

import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.StakingStoryModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel

interface StakingRouter {

    fun openSetupStaking()

    fun openRecommendedValidators()

    fun openValidatorDetails(validatorDetails: ValidatorDetailsParcelModel)

    fun openConfirmStaking()

    fun openConfirmNominations()

    fun finishSetupStakingFlow()

    fun openChangeAccountFromStaking()

    fun openStory(story: StakingStoryModel)

    fun back()
}
