package jp.co.soramitsu.feature_staking_impl.domain.validations.unbond

import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrWarning
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks

class CrossExistentialValidation(
    val walletConstants: WalletConstants,
) : UnbondValidation {

    override suspend fun validate(value: UnbondValidationPayload): ValidationStatus<UnbondValidationFailure> {
        val existentialDepositInPlanks = walletConstants.existentialDeposit()
        val existentialDeposit = value.tokenType.amountFromPlanks(existentialDepositInPlanks)

        val bonded = value.asset.bonded
        val resultGreaterThanExistential = bonded - value.amount >= existentialDeposit
        val resultIsZero = bonded == value.amount

        return validOrWarning(resultGreaterThanExistential || resultIsZero) {
            UnbondValidationFailure.BondedWillCrossExistential(willBeUnbonded = bonded)
        }
    }
}
