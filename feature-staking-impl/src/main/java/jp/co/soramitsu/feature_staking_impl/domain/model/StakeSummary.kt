package jp.co.soramitsu.feature_staking_impl.domain.model

import java.math.BigDecimal

class StakeSummary<S>(
    val status: S,
    val totalStaked: BigDecimal,
    val totalRewards: BigDecimal,
    val currentEra: Int,
)

sealed class NominatorStatus {
    object Active : NominatorStatus()
    object Waiting : NominatorStatus()

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
