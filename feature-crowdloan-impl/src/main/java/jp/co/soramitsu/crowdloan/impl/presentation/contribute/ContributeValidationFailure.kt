package jp.co.soramitsu.crowdloan.impl.presentation.contribute

import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.crowdloan.impl.domain.contribute.validations.ContributeValidationFailure
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount

fun contributeValidationFailure(
    reason: ContributeValidationFailure,
    resourceManager: ResourceManager
): TitleAndMessage {
    return when (reason) {
        ContributeValidationFailure.CannotPayFees -> {
            resourceManager.getString(R.string.common_not_enough_funds_title) to
                resourceManager.getString(R.string.common_not_enough_funds_message)
        }

        ContributeValidationFailure.ExistentialDepositCrossed -> {
            resourceManager.getString(R.string.common_existential_warning_title) to resourceManager.getString(R.string.common_existential_warning_message)
        }

        ContributeValidationFailure.CrowdloanEnded -> {
            resourceManager.getString(R.string.crowdloan_ended_title) to
                resourceManager.getString(R.string.crowdloan_ended_message)
        }

        ContributeValidationFailure.CapExceeded.FromRaised -> {
            resourceManager.getString(R.string.crowdloan_cap_reached_title) to
                resourceManager.getString(R.string.crowdloan_cap_reached_raised_message)
        }

        is ContributeValidationFailure.CapExceeded.FromAmount -> {
            val formattedAmount = with(reason) {
                maxAllowedContribution.formatTokenAmount(chainAsset)
            }

            resourceManager.getString(R.string.crowdloan_cap_reached_title) to
                resourceManager.getString(R.string.crowdloan_cap_reached_amount_message, formattedAmount)
        }

        is ContributeValidationFailure.LessThanMinContribution -> {
            val formattedAmount = with(reason) {
                minContribution.formatTokenAmount(chainAsset)
            }

            resourceManager.getString(R.string.crowdloan_too_small_contribution_title) to
                resourceManager.getString(R.string.crowdloan_too_small_contribution_message, formattedAmount)
        }

        ContributeValidationFailure.PrivateCrowdloanNotSupported -> {
            resourceManager.getString(R.string.crodloan_private_crowdloan_title) to
                resourceManager.getString(R.string.crodloan_private_crowdloan_message)
        }
    }
}
