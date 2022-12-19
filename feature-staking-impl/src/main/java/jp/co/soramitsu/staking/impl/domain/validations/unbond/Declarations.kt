package jp.co.soramitsu.staking.impl.domain.validations.unbond

import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.NotZeroAmountValidation
import jp.co.soramitsu.staking.impl.domain.validations.UnbondingRequestsLimitValidation
import jp.co.soramitsu.wallet.impl.domain.validation.EnoughToPayFeesValidation

typealias UnbondFeeValidation = EnoughToPayFeesValidation<UnbondValidationPayload, UnbondValidationFailure>
typealias NotZeroUnbondValidation = NotZeroAmountValidation<UnbondValidationPayload, UnbondValidationFailure>
typealias UnbondLimitValidation = UnbondingRequestsLimitValidation<UnbondValidationPayload, UnbondValidationFailure>

typealias UnbondValidation = Validation<UnbondValidationPayload, UnbondValidationFailure>

typealias UnbondValidationSystem = ValidationSystem<UnbondValidationPayload, UnbondValidationFailure>
