package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute

import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationFailure

fun contributeValidationFailure(
    reason: ContributeValidationFailure,
    resourceManager: ResourceManager
): TitleAndMessage {
    return when (reason) {
        ContributeValidationFailure.CannotPayFees -> {
            resourceManager.getString(R.string.common_not_enough_funds_title) to
                resourceManager.getString(R.string.common_not_enough_funds_message)
        }
        ContributeValidationFailure.ExistentialDepositCrossed ->
            resourceManager.getString(R.string.common_existential_warning_title) to resourceManager.getString(R.string.common_existential_warning_message)
    }
}
