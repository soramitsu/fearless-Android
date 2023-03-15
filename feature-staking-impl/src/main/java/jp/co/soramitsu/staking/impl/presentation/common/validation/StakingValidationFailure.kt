package jp.co.soramitsu.staking.impl.presentation.common.validation

import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount

fun stakingValidationFailure(
    payload: SetupStakingPayload,
    reason: SetupStakingValidationFailure,
    resourceManager: ResourceManager
): TitleAndMessage {
    val (title, message) = with(resourceManager) {
        when (reason) {
            SetupStakingValidationFailure.CannotPayFee -> {
                getString(R.string.common_not_enough_funds_title) to getString(R.string.choose_amount_error_too_big)
            }

            is SetupStakingValidationFailure.TooSmallAmount -> {
                val formattedThreshold = reason.threshold.formatTokenAmount(payload.asset.token.configuration)

                getString(R.string.common_amount_low) to getString(R.string.staking_setup_amount_too_low, formattedThreshold)
            }

            SetupStakingValidationFailure.MaxNominatorsReached -> {
                getString(R.string.staking_max_nominators_reached_title) to getString(R.string.staking_max_nominators_reached_message)
            }
        }
    }

    return title to message
}
