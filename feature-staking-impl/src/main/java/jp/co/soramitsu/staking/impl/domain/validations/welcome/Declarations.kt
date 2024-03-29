package jp.co.soramitsu.staking.impl.domain.validations.welcome

import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.MaxNominatorsReachedValidation

typealias WelcomeStakingValidationSystem = ValidationSystem<WelcomeStakingValidationPayload, WelcomeStakingValidationFailure>

typealias WelcomeStakingMaxNominatorsValidation = MaxNominatorsReachedValidation<WelcomeStakingValidationPayload, WelcomeStakingValidationFailure>
