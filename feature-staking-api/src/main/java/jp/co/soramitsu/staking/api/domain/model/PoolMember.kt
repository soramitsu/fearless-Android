package jp.co.soramitsu.staking.api.domain.model

import android.os.Parcelable
import jp.co.soramitsu.shared_utils.runtime.AccountId
import java.math.BigInteger

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

data class OwnPool(
    val poolId: BigInteger,
    val name: String?,
    val myStakeInPlanks: BigInteger,
    val totalStakedInPlanks: BigInteger,
    val lastRecordedRewardCounter: BigInteger,
    val state: NominationPoolState,
    val redeemable: BigInteger,
    val unbonding: BigInteger,
    val pendingRewards: BigInteger,
    val members: BigInteger,
    val depositor: AccountId,
    val root: AccountId?,
    val nominator: AccountId?,
    val stateToggler: AccountId?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OwnPool

        if (poolId != other.poolId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId.hashCode()
        result = 31 * result + myStakeInPlanks.hashCode()
        result = 31 * result + lastRecordedRewardCounter.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + redeemable.hashCode()
        result = 31 * result + members.hashCode()
        result = 31 * result + depositor.contentHashCode()
        result = 31 * result + root.contentHashCode()
        result = 31 * result + nominator.contentHashCode()
        result = 31 * result + stateToggler.contentHashCode()
        result = 31 * result + unbonding.hashCode()
        return result
    }
}

fun OwnPool.getUserRole(accountId: AccountId): RoleInPool? {
    return when {
        accountId.contentEquals(depositor) -> RoleInPool.Depositor
        accountId.contentEquals(root) -> RoleInPool.Root
        accountId.contentEquals(stateToggler) -> RoleInPool.StateToggler
        accountId.contentEquals(nominator) -> RoleInPool.Nominator
        else -> null
    }
}

fun OwnPool.toPoolInfo(): PoolInfo {
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
    Open, Blocked, Destroying, HasNoValidators;

    companion object {
        fun from(value: String) = when (value) {
            "Open" -> Open
            "Blocked" -> Blocked
            "Destroying" -> Destroying
            else -> error("Nomination pool state cannot be parsed")
        }
    }
}

enum class RoleInPool {
    Depositor, Root, Nominator, StateToggler
}
