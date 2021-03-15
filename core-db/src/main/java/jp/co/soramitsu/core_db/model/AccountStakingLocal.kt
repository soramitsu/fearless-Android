package jp.co.soramitsu.core_db.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey

@Suppress("EqualsOrHashCode")
@Entity(
    tableName = "account_staking_accesses",
    foreignKeys = [
        ForeignKey(
            entity = AccountLocal::class,
            parentColumns = ["address"],
            childColumns = ["address"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    primaryKeys = ["address"]
)
class AccountStakingLocal(
    val address: String,
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
            other.address == address &&
            other.stakingAccessInfo == stakingAccessInfo
    }
}
