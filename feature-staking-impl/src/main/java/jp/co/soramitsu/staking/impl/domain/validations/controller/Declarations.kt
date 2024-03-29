package jp.co.soramitsu.staking.impl.domain.validations.controller

import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.AccountIsNotControllerValidation
import jp.co.soramitsu.wallet.impl.domain.validation.EnoughToPayFeesValidation

typealias SetControllerFeeValidation = EnoughToPayFeesValidation<SetControllerValidationPayload, SetControllerValidationFailure>
typealias IsNotControllerAccountValidation = AccountIsNotControllerValidation<SetControllerValidationPayload, SetControllerValidationFailure>

typealias SetControllerValidationSystem = ValidationSystem<SetControllerValidationPayload, SetControllerValidationFailure>
