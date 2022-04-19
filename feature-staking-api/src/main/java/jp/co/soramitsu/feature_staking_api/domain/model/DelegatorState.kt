package jp.co.soramitsu.feature_staking_api.domain.model

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.runtime.AccountId

data class DelegatorState(
    val id: AccountId,
    val delegations: List<Delegation>,
    val total: BigInteger,
    val status: DelegatorStateStatus
)

data class Delegation(
    val owner: AccountId,
    val amount: BigInteger
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
