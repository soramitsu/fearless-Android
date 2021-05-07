package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller

import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationFailure

fun bondSetControllerValidationFailure(
    reason: SetControllerValidationFailure,
    resourceManager: ResourceManager
): TitleAndMessage {
    return when (reason) {
        SetControllerValidationFailure.ALREADY_CONTROLLER -> {
            resourceManager.getString(R.string.staking_already_controller_title) to
                resourceManager.getString(R.string.staking_already_controller_message)
        }
        SetControllerValidationFailure.NOT_ENOUGH_TO_PAY_FEES -> {
            resourceManager.getString(R.string.common_not_enough_funds_title) to
                resourceManager.getString(R.string.common_not_enough_funds_message)
        }
    }
}
