package jp.co.soramitsu.staking.impl.domain.validations.rebond

import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.NotZeroAmountValidation
import jp.co.soramitsu.wallet.impl.domain.validation.EnoughToPayFeesValidation

typealias RebondFeeValidation = EnoughToPayFeesValidation<RebondValidationPayload, RebondValidationFailure>
typealias NotZeroRebondValidation = NotZeroAmountValidation<RebondValidationPayload, RebondValidationFailure>

typealias RebondValidation = Validation<RebondValidationPayload, RebondValidationFailure>

typealias RebondValidationSystem = ValidationSystem<RebondValidationPayload, RebondValidationFailure>
