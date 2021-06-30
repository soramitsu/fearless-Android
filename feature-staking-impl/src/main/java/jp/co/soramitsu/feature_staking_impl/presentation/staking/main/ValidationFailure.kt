package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.validations.welcome.WelcomeStakingValidationFailure

fun welcomeStakingValidationFailure(
    reason: WelcomeStakingValidationFailure,
    resourceManager: ResourceManager
): TitleAndMessage = with(resourceManager) {
    when (reason) {
        WelcomeStakingValidationFailure.MAX_NOMINATORS_REACHED -> {
            getString(R.string.staking_max_nominators_reached_title) to getString(R.string.staking_max_nominators_reached_message)
        }
    }
}
