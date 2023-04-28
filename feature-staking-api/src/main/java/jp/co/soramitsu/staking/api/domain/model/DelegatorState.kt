package jp.co.soramitsu.staking.api.domain.model

import jp.co.soramitsu.shared_utils.runtime.AccountId
import java.math.BigInteger

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
    ACTIVE, EMPTY, LEAVING, IDLE;

    companion object {
        fun from(key: String?) = when (key) {
            "Active" -> ACTIVE
            "Leaving" -> LEAVING
            "Idle" -> IDLE
            else -> EMPTY
        }
    }
}
