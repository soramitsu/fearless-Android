package jp.co.soramitsu.feature_staking_impl.domain.model

import java.math.BigDecimal

class NominatorSummary(
    val status: Status,
    val totalStaked: BigDecimal,
    val totalRewards: BigDecimal
) {
    enum class Status {
        ACTIVE, INACTIVE, WAITING, ELECTION
    }
}
