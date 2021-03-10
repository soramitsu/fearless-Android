package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.ForeignKey

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
    val stashId: ByteArray?,
    val controllerId: ByteArray?
)