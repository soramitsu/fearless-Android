package jp.co.soramitsu.feature_staking_api.domain.model

import java.math.BigInteger

class NominatedValidator(
    val validator: Validator,
    val nominationInPlanks: BigInteger?,
)

sealed class NominatedValidatorStatus {
    object Active : NominatedValidatorStatus()
    object Elected : NominatedValidatorStatus()
    object Inactive : NominatedValidatorStatus()

    class WaitingForNextEra(val maxValidatorsPerNominator: Int) : NominatedValidatorStatus()
}
