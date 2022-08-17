package jp.co.soramitsu.staking.impl.presentation.staking.rebond

import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationFailure

fun rebondValidationFailure(
    reason: RebondValidationFailure,
    resourceManager: ResourceManager
): TitleAndMessage {
    return when (reason) {
        RebondValidationFailure.CANNOT_PAY_FEE -> {
            resourceManager.getString(R.string.common_not_enough_funds_title) to
                resourceManager.getString(R.string.common_not_enough_funds_message)
        }

        RebondValidationFailure.NOT_ENOUGH_UNBONDINGS -> {
            resourceManager.getString(R.string.common_not_enough_funds_title) to
                resourceManager.getString(R.string.staking_rebond_insufficient_bondings)
        }

        RebondValidationFailure.ZERO_AMOUNT -> {
            resourceManager.getString(R.string.common_error_general_title) to
                resourceManager.getString(R.string.staking_zero_bond_error)
        }
    }
}
