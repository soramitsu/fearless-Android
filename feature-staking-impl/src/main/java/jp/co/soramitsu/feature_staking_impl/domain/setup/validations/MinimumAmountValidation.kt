package jp.co.soramitsu.feature_staking_impl.domain.setup.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.SetupStakingPayload
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks

class MinimumAmountValidation(
    private val walletConstants: WalletConstants,
) : Validation<SetupStakingPayload, StakingValidationFailure> {

    override suspend fun validate(value: SetupStakingPayload): ValidationStatus<StakingValidationFailure> {

        val existentialDepositInPlanks = walletConstants.existentialDeposit()
        val existentialDeposit = value.tokenType.amountFromPlanks(existentialDepositInPlanks)

        return if (value.amount >= existentialDeposit) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, StakingValidationFailure.TooSmallAmount(existentialDeposit))
        }
    }
}
