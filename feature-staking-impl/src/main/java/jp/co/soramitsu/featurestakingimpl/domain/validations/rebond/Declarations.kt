package jp.co.soramitsu.featurestakingimpl.domain.validations.rebond

import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.featurestakingimpl.domain.validations.NotZeroAmountValidation
import jp.co.soramitsu.featurewalletapi.domain.validation.EnoughToPayFeesValidation

typealias RebondFeeValidation = EnoughToPayFeesValidation<RebondValidationPayload, RebondValidationFailure>
typealias NotZeroRebondValidation = NotZeroAmountValidation<RebondValidationPayload, RebondValidationFailure>

typealias RebondValidation = Validation<RebondValidationPayload, RebondValidationFailure>

typealias RebondValidationSystem = ValidationSystem<RebondValidationPayload, RebondValidationFailure>
