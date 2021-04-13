package jp.co.soramitsu.feature_staking_impl.domain.validations.setup

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks

class MinimumAmountValidation(
    private val walletConstants: WalletConstants,
) : Validation<SetupStakingPayload, SetupStakingValidtionFailure> {

    override suspend fun validate(value: SetupStakingPayload): ValidationStatus<SetupStakingValidtionFailure> {

        val existentialDepositInPlanks = walletConstants.existentialDeposit()
        val existentialDeposit = value.tokenType.amountFromPlanks(existentialDepositInPlanks)

        return if (value.amount >= existentialDeposit) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, SetupStakingValidtionFailure.TooSmallAmount(existentialDeposit))
        }
    }
}
