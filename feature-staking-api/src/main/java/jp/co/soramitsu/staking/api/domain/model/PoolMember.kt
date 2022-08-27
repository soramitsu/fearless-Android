package jp.co.soramitsu.staking.api.domain.model

import java.math.BigInteger
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.fearless_utils.runtime.AccountId

data class PoolMember(
    val poolId: BigInteger,
    val stakedInPlanks: BigInteger,
    val lastRecordedRewardCounter: BigInteger,
    val unbondingEras: List<PoolUnbonding>
)

data class PoolUnbonding(
    val era: BigInteger,
    val amount: BigInteger
)

data class NominationPool(
    val poolId: BigInteger,
    val name: String?,
    val stakedInPlanks: BigInteger,
    val lastRecordedRewardCounter: BigInteger,
    val state: NominationPoolState,
    val redeemable: BigInteger,
    val unbondingEras: List<PoolUnbonding>,
    val members: BigInteger,
    val depositor: AccountId,
    val root: AccountId,
    val nominator: AccountId,
    val stateToggler: AccountId
) {
    val unstaking = unbondingEras.sumByBigInteger { it.amount }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NominationPool

        if (poolId != other.poolId) return false
        if (stakedInPlanks != other.stakedInPlanks) return false
        if (lastRecordedRewardCounter != other.lastRecordedRewardCounter) return false
        if (state != other.state) return false
        if (redeemable != other.redeemable) return false
        if (unbondingEras != other.unbondingEras) return false
        if (members != other.members) return false
        if (!depositor.contentEquals(other.depositor)) return false
        if (!root.contentEquals(other.root)) return false
        if (!nominator.contentEquals(other.nominator)) return false
        if (!stateToggler.contentEquals(other.stateToggler)) return false
        if (unstaking != other.unstaking) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId.hashCode()
        result = 31 * result + stakedInPlanks.hashCode()
        result = 31 * result + lastRecordedRewardCounter.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + redeemable.hashCode()
        result = 31 * result + unbondingEras.hashCode()
        result = 31 * result + members.hashCode()
        result = 31 * result + depositor.contentHashCode()
        result = 31 * result + root.contentHashCode()
        result = 31 * result + nominator.contentHashCode()
        result = 31 * result + stateToggler.contentHashCode()
        result = 31 * result + unstaking.hashCode()
        return result
    }
}

enum class NominationPoolState {
    Open, Blocked, Destroying;

    companion object {
        fun from(value: String) = when (value) {
            "Open" -> Open
            "Blocked" -> Blocked
            "Destroying" -> Destroying
            else -> error("Nomination pool state cannot be parsed")
        }
    }
}
