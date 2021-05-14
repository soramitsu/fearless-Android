package jp.co.soramitsu.feature_staking_impl.presentation.common.validation

import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount

fun stakingValidationFailure(
    payload: SetupStakingPayload,
    reason: SetupStakingValidationFailure,
    resourceManager: ResourceManager,
): TitleAndMessage {
    val (title, message) = with(resourceManager) {
        when (reason) {
            SetupStakingValidationFailure.CannotPayFee -> {
                getString(R.string.common_error_general_title) to getString(R.string.staking_setup_too_big_error)
            }

            is SetupStakingValidationFailure.TooSmallAmount -> {
                val formattedThreshold = reason.threshold.formatTokenAmount(payload.tokenType)

                getString(R.string.common_amount_low) to getString(R.string.staking_setup_amount_too_low, formattedThreshold)
            }
        }
    }

    return title to message
}
