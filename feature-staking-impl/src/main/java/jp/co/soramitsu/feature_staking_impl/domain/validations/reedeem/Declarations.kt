package jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem

import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation

typealias RedeemFeeValidation = EnoughToPayFeesValidation<RedeemValidationPayload, RedeemValidationFailure>

typealias RedeemValidationSystem = ValidationSystem<RedeemValidationPayload, RedeemValidationFailure>
