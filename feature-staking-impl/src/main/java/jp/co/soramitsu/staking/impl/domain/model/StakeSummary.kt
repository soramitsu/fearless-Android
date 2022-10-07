package jp.co.soramitsu.staking.impl.domain.model

import java.math.BigDecimal

class StakeSummary<S>(
    val status: S,
    val totalStaked: BigDecimal,
    val totalReward: BigDecimal,
    val currentEra: Int
)

sealed class NominatorStatus {
    object Active : NominatorStatus()

    class Waiting(val timeLeft: Long) : NominatorStatus()

    class Inactive(val reason: Reason) : NominatorStatus() {

        enum class Reason {
            MIN_STAKE, NO_ACTIVE_VALIDATOR
        }
    }
}

enum class StashNoneStatus {
    INACTIVE
}

enum class ValidatorStatus {
    ACTIVE, INACTIVE
}

sealed class DelegatorStatus {
    object Active : DelegatorStatus()

    class Waiting(val timeLeft: Long) : DelegatorStatus()

    class Inactive(val reason: Reason) : DelegatorStatus() {

        enum class Reason {
            MIN_STAKE, NO_ACTIVE_VALIDATOR
        }
    }
}
