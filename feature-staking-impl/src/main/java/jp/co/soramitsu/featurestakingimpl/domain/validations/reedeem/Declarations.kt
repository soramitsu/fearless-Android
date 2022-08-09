package jp.co.soramitsu.featurestakingimpl.domain.validations.reedeem

import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.featurewalletapi.domain.validation.EnoughToPayFeesValidation

typealias RedeemFeeValidation = EnoughToPayFeesValidation<RedeemValidationPayload, RedeemValidationFailure>

typealias RedeemValidationSystem = ValidationSystem<RedeemValidationPayload, RedeemValidationFailure>
