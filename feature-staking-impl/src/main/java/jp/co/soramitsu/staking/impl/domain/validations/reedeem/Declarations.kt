package jp.co.soramitsu.staking.impl.domain.validations.reedeem

import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.wallet.impl.domain.validation.EnoughToPayFeesValidation

typealias RedeemFeeValidation = EnoughToPayFeesValidation<RedeemValidationPayload, RedeemValidationFailure>

typealias RedeemValidationSystem = ValidationSystem<RedeemValidationPayload, RedeemValidationFailure>
