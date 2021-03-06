package jp.co.soramitsu.feature_staking_impl.domain.validations.bond

import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.domain.validations.NotZeroAmountValidation
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation

typealias BondMoreFeeValidation = EnoughToPayFeesValidation<BondMoreValidationPayload, BondMoreValidationFailure>
typealias NotZeroBondValidation = NotZeroAmountValidation<BondMoreValidationPayload, BondMoreValidationFailure>

typealias BondMoreValidationSystem = ValidationSystem<BondMoreValidationPayload, BondMoreValidationFailure>
