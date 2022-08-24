package jp.co.soramitsu.staking.impl.domain.validations.bond

import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.NotZeroAmountValidation
import jp.co.soramitsu.wallet.impl.domain.validation.EnoughToPayFeesValidation

typealias BondMoreFeeValidation = EnoughToPayFeesValidation<BondMoreValidationPayload, BondMoreValidationFailure>
typealias NotZeroBondValidation = NotZeroAmountValidation<BondMoreValidationPayload, BondMoreValidationFailure>

typealias BondMoreValidationSystem = ValidationSystem<BondMoreValidationPayload, BondMoreValidationFailure>
