package jp.co.soramitsu.feature_staking_api.domain.model

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import java.math.BigInteger

data class DelegationScheduledRequest(
    val delegator: AccountId,
    val whenExecutable: BigInteger,
    val action: DelegationAction,
    val actionValue: BigInteger
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DelegationScheduledRequest

        if (!delegator.contentEquals(other.delegator)) return false
        if (whenExecutable != other.whenExecutable) return false
        if (action != other.action) return false
        if (actionValue != other.actionValue) return false

        return true
    }

    override fun hashCode(): Int {
        var result = delegator.contentHashCode()
        result = 31 * result + whenExecutable.hashCode()
        result = 31 * result + action.hashCode()
        result = 31 * result + actionValue.hashCode()
        return result
    }
}

enum class DelegationAction {
    REVOKE, DECREASE, OTHER;

    companion object {
        fun from(key: String?) = when (key) {
            "Revoke" -> REVOKE
            "Decrease" -> DECREASE
            else -> OTHER
        }
    }
}
