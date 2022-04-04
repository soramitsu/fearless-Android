package jp.co.soramitsu.feature_staking_api.domain.model

import java.math.BigDecimal

data class DelegatorState(
    val id: String,
    val delegations: List<Delegation>,
    val total: BigDecimal,
    val status: DelegatorStateStatus
)

data class Delegation(
    val owner: String,
    val amount: BigDecimal
)

enum class DelegatorStateStatus {
    Active, Empty;

    companion object {
        fun from(key: String?) = when (key) {
            "Active" -> Active
            else -> Empty
        }
    }
}
