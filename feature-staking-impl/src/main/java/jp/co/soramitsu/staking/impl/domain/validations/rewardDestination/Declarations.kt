package jp.co.soramitsu.staking.impl.domain.validations.rewardDestination

import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.AccountRequiredValidation
import jp.co.soramitsu.wallet.impl.domain.validation.EnoughToPayFeesValidation

typealias RewardDestinationFeeValidation = EnoughToPayFeesValidation<RewardDestinationValidationPayload, RewardDestinationValidationFailure>
typealias RewardDestinationControllerRequiredValidation = AccountRequiredValidation<RewardDestinationValidationPayload, RewardDestinationValidationFailure>

typealias RewardDestinationValidationSystem = ValidationSystem<RewardDestinationValidationPayload, RewardDestinationValidationFailure>
