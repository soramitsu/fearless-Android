package jp.co.soramitsu.feature_staking_impl.presentation.common.validation

import jp.co.soramitsu.common.mixin.api.DefaultFailure
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.model.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.setup.validations.StakingValidationFailure
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatWithDefaultPrecision

fun stakingValidationFailure(
    payload: SetupStakingPayload,
    status: ValidationStatus.NotValid<StakingValidationFailure>,
    resourceManager: ResourceManager
): DefaultFailure {
    val (titleRes, messageRes) = with(resourceManager) {
        when (val reason = status.reason) {
            StakingValidationFailure.CannotPayFee -> {
                getString(R.string.common_error_general_title) to getString(R.string.staking_setup_too_big_error)
            }

            is StakingValidationFailure.TooSmallAmount -> {
                val formattedThreshold = reason.threshold.formatWithDefaultPrecision(payload.tokenType)

                getString(R.string.common_amount_low) to getString(R.string.staking_setup_amount_too_low, formattedThreshold)
            }
        }
    }

    return DefaultFailure(
        level = status.level,
        title = titleRes,
        message = messageRes
    )
}
