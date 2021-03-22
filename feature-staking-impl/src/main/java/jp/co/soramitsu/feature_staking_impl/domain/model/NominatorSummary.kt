package jp.co.soramitsu.feature_staking_impl.domain.model

import java.math.BigDecimal

class NominatorSummary(
    val status: Status,
    val totalStaked: BigDecimal,
    val totalRewards: BigDecimal,
    val currentEra: Int
) {
    sealed class Status {
        object Active : Status()
        object Waiting : Status()
        object Election : Status()

        class Inactive(val reason: Reason) : Status() {

            enum class Reason {
                MIN_STAKE, NO_ACTIVE_VALIDATOR
            }
        }
    }
}
