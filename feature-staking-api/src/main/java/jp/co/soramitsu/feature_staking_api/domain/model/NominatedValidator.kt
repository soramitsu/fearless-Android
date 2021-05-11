package jp.co.soramitsu.feature_staking_api.domain.model

import java.math.BigInteger

class NominatedValidator(
    val validator: Validator,
    val nominationInPlanks: BigInteger?,
    val status: Status
) {

    sealed class Status(val groupPosition: Int) {
        companion object {
            val COMPARATOR = Comparator.comparingInt<Status> { it.groupPosition }
        }

        object Active : Status(0)
        object Elected : Status(1)
        object Inactive : Status(2)

        class WaitingForNextEra(val maxValidatorsPerNominator: Int) : Status(3)
    }
}
