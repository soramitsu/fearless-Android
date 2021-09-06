package jp.co.soramitsu.feature_staking_api.domain.model

import java.math.BigInteger

class NominatedValidator(
    val validator: Validator,
    val status: Status,
) {

    sealed class Status {

        class Active(val nomination: BigInteger, val willUserBeRewarded: Boolean) : Status()
        object Elected : Status()
        object Inactive : Status()
        object WaitingForNextEra : Status()

        sealed class Group(val position: Int) {
            companion object {
                val COMPARATOR = Comparator.comparingInt<Group> { it.position }
            }

            object Active : Group(0)
            object Elected : Group(1)
            object Inactive : Group(2)
            class WaitingForNextEra(val maxValidatorsPerNominator: Int) : Group(3)
        }
    }
}
