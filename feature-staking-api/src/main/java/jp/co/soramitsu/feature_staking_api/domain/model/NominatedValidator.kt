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

        sealed class Group(val numberOfValidators: Int, val position: Int) {
            companion object {
                val COMPARATOR = Comparator.comparingInt<Group> { it.position }
            }

            class Active(numberOfValidators: Int) : Group(numberOfValidators, 0)
            class Elected(numberOfValidators: Int) : Group(numberOfValidators, 1)
            class Inactive(numberOfValidators: Int) : Group(numberOfValidators, 2)
            class WaitingForNextEra(val maxValidatorsPerNominator: Int, numberOfValidators: Int) : Group(numberOfValidators, 3)
        }
    }
}
