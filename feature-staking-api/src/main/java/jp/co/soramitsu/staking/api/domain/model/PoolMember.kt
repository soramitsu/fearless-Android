package jp.co.soramitsu.staking.api.domain.model

import android.os.Parcelable
import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.runtime.AccountId

data class PoolUnbonding(
    val era: BigInteger,
    val amount: BigInteger
)

@kotlinx.parcelize.Parcelize
data class PoolInfo(
    val poolId: BigInteger,
    val name: String,
    val stakedInPlanks: BigInteger,
    val state: NominationPoolState,
    val members: BigInteger,
    val depositor: AccountId,
    val root: AccountId?,
    val nominator: AccountId?,
    val stateToggler: AccountId?
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PoolInfo

        if (poolId != other.poolId) return false

        return true
    }

    override fun hashCode(): Int {
        return poolId.hashCode()
    }
}

data class NominationPool(
    val poolId: BigInteger,
    val name: String?,
    val myStakeInPlanks: BigInteger,
    val totalStakedInPlanks: BigInteger,
    val lastRecordedRewardCounter: BigInteger,
    val state: NominationPoolState,
    val redeemable: BigInteger,
    val unbonding: BigInteger,
    val unbondingEras: List<PoolUnbonding>,
    val members: BigInteger,
    val depositor: AccountId,
    val root: AccountId?,
    val nominator: AccountId?,
    val stateToggler: AccountId?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NominationPool

        if (poolId != other.poolId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId.hashCode()
        result = 31 * result + myStakeInPlanks.hashCode()
        result = 31 * result + lastRecordedRewardCounter.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + redeemable.hashCode()
        result = 31 * result + unbondingEras.hashCode()
        result = 31 * result + members.hashCode()
        result = 31 * result + depositor.contentHashCode()
        result = 31 * result + root.contentHashCode()
        result = 31 * result + nominator.contentHashCode()
        result = 31 * result + stateToggler.contentHashCode()
        result = 31 * result + unbonding.hashCode()
        return result
    }
}

fun NominationPool.toPoolInfo(): PoolInfo {
    return PoolInfo(
        poolId,
        name ?: "Pool #$poolId",
        totalStakedInPlanks,
        state,
        members,
        depositor,
        root,
        nominator,
        stateToggler
    )
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
