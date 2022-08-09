package jp.co.soramitsu.featurestakingapi.domain.model

import androidx.annotation.StringRes
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_staking_api.R
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

enum class DelegationAction(@StringRes val nameResId: Int?) {
    STAKE(R.string.staking_stake),
    UNSTAKE(R.string.staking_unbond_v1_9_0),
    REWARD(R.string.staking_reward),
    DELEGATE(R.string.staking_delegate),
    OTHER(null);

    companion object {
        fun from(key: String?) = when (key) {
            "Revoke" -> UNSTAKE
            "Decrease" -> UNSTAKE
            else -> OTHER
        }

        fun byId(id: Int?) = when (id) {
            0 -> STAKE
            1 -> UNSTAKE
            2 -> REWARD
            3 -> DELEGATE
            else -> OTHER
        }
    }
}
