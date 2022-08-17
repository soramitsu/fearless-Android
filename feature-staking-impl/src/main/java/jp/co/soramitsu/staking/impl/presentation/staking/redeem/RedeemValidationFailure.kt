package jp.co.soramitsu.staking.impl.presentation.staking.redeem

import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemValidationFailure

fun redeemValidationFailure(
    reason: RedeemValidationFailure,
    resourceManager: ResourceManager
): TitleAndMessage {
    return when (reason) {
        RedeemValidationFailure.CANNOT_PAY_FEES -> {
            resourceManager.getString(R.string.common_not_enough_funds_title) to
                resourceManager.getString(R.string.common_not_enough_funds_message)
        }
    }
}
