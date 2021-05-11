package jp.co.soramitsu.feature_staking_api.domain.model

import java.math.BigInteger

class NominatedValidator(
    val validator: Validator,
    val nominationInPlanks: BigInteger?,
)

sealed class NominatedValidatorStatus(val groupPosition: Int) {
    companion object {
        val COMPARATOR = Comparator.comparingInt<NominatedValidatorStatus> { it.groupPosition }
    }

    object Active : NominatedValidatorStatus(0)
    object Elected : NominatedValidatorStatus(1)
    object Inactive : NominatedValidatorStatus(2)

    class WaitingForNextEra(val maxValidatorsPerNominator: Int) : NominatedValidatorStatus(3)
}
