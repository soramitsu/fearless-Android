package jp.co.soramitsu.feature_staking_impl.domain.validations.balance

import jp.co.soramitsu.feature_staking_impl.domain.validations.AccountRequiredValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.UnbondingRequestsLimitValidation

const val BALANCE_REQUIRED_STASH = "BalanceAccountRequiredValidation.Stash"
const val BALANCE_REQUIRED_CONTROLLER = "BalanceAccountRequiredValidation.Controller"

typealias BalanceAccountRequiredValidation = AccountRequiredValidation<ManageStakingValidationPayload, ManageStakingValidationFailure>
typealias BalanceUnlockingLimitValidation = UnbondingRequestsLimitValidation<ManageStakingValidationPayload, ManageStakingValidationFailure>
