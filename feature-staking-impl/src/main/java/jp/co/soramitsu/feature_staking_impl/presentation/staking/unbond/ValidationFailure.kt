package jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond

import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationPayload

fun unbondValidationFailure(
    reason: UnbondValidationFailure,
    resourceManager: ResourceManager
): TitleAndMessage {
    return when (reason) {
        is UnbondValidationFailure.BondedWillCrossExistential -> {
            resourceManager.getString(R.string.common_warning) to
                resourceManager.getString(R.string.staking_unbond_crossed_existential)
        }

        UnbondValidationFailure.CannotPayFees -> {
            resourceManager.getString(R.string.common_not_enough_funds_title) to
                resourceManager.getString(R.string.common_not_enough_funds_message)
        }

        UnbondValidationFailure.NotEnoughBonded -> {
            resourceManager.getString(R.string.common_not_enough_funds_title) to
                resourceManager.getString(R.string.staking_rebond_insufficient_bondings)
        }

        is UnbondValidationFailure.UnbondLimitReached -> {
            resourceManager.getString(R.string.staking_unbonding_limit_reached_title) to
                resourceManager.getString(R.string.staking_unbonding_limit_reached_message, reason.limit)
        }

        UnbondValidationFailure.ZeroUnbond -> {
            resourceManager.getString(R.string.common_error_general_title) to
                resourceManager.getString(R.string.staking_zero_bond_error)
        }
    }
}

fun unbondPayloadAutoFix(payload: UnbondValidationPayload, reason: UnbondValidationFailure) = when (reason) {
    is UnbondValidationFailure.BondedWillCrossExistential -> payload.copy(amount = reason.willBeUnbonded)
    else -> payload
}
