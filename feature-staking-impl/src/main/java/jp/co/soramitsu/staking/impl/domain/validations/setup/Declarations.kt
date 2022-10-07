package jp.co.soramitsu.staking.impl.domain.validations.setup

import jp.co.soramitsu.staking.impl.domain.validations.MaxNominatorsReachedValidation
import jp.co.soramitsu.wallet.impl.domain.validation.EnoughToPayFeesValidation

typealias SetupStakingFeeValidation = EnoughToPayFeesValidation<SetupStakingPayload, SetupStakingValidationFailure>
typealias SetupStakingMaximumNominatorsValidation = MaxNominatorsReachedValidation<SetupStakingPayload, SetupStakingValidationFailure>