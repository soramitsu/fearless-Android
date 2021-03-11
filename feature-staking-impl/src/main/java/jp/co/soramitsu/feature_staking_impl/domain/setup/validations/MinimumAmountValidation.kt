package jp.co.soramitsu.feature_staking_impl.domain.setup.validations

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.feature_staking_impl.domain.model.SetupStakingPayload
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks

class MinimumAmountValidation(
    val runtimeProperty: SuspendableProperty<RuntimeSnapshot>
) : Validation<SetupStakingPayload, StakingValidationFailure> {

    override suspend fun validate(value: SetupStakingPayload): ValidationStatus<StakingValidationFailure> {
        val runtime = runtimeProperty.get()

        val existentialDepositInPlanks = runtime.metadata.module("Balances").numberConstant("ExistentialDeposit", runtime)
        val existentialDeposit = value.tokenType.amountFromPlanks(existentialDepositInPlanks)

        return if (value.amount >= existentialDeposit) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, StakingValidationFailure.TooSmallAmount(existentialDeposit))
        }
    }
}