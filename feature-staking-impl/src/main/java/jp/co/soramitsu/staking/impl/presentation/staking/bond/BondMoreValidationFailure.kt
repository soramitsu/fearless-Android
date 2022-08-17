package jp.co.soramitsu.staking.impl.presentation.staking.bond

import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationFailure

fun bondMoreValidationFailure(
    reason: BondMoreValidationFailure,
    resourceManager: ResourceManager
): TitleAndMessage {
    return when (reason) {
        BondMoreValidationFailure.NOT_ENOUGH_TO_PAY_FEES -> {
            resourceManager.getString(R.string.common_not_enough_funds_title) to
                resourceManager.getString(R.string.common_not_enough_funds_message)
        }

        BondMoreValidationFailure.ZERO_BOND -> {
            resourceManager.getString(R.string.common_error_general_title) to
                resourceManager.getString(R.string.staking_zero_bond_error)
        }
    }
}
