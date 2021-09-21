package jp.co.soramitsu.core_db.model

import androidx.room.Embedded
import androidx.room.Entity

@Suppress("EqualsOrHashCode")
@Entity(
    tableName = "account_staking_accesses",
    primaryKeys = ["chainId", "accountId"]
)
class AccountStakingLocal(
    val chainId: String,
    val accountId: ByteArray,
    @Embedded
    val stakingAccessInfo: AccessInfo?
) {
    class AccessInfo(val stashId: ByteArray, val controllerId: ByteArray) {

        override fun equals(other: Any?): Boolean {
            return other is AccessInfo &&
                stashId.contentEquals(other.stashId) &&
                controllerId.contentEquals(other.controllerId)
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is AccountStakingLocal &&
            other.chainId == chainId &&
            other.accountId.contentEquals(accountId) &&
            other.stakingAccessInfo == stakingAccessInfo
    }
}
