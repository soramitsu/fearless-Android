package jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination

import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.domain.validations.AccountRequiredValidation
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation

typealias RewardDestinationFeeValidation = EnoughToPayFeesValidation<RewardDestinationValidationPayload, RewardDestinationValidationFailure>
typealias RewardDestinationControllerRequiredValidation = AccountRequiredValidation<RewardDestinationValidationPayload, RewardDestinationValidationFailure>

typealias RewardDestinationValidationSystem = ValidationSystem<RewardDestinationValidationPayload, RewardDestinationValidationFailure>
