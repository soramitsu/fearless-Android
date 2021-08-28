package jp.co.soramitsu.core_db.model.chain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "chain_runtimes",
    primaryKeys = ["chainId"],
    foreignKeys = [
        ForeignKey(
            entity = ChainLocal::class,
            parentColumns = ["id"],
            childColumns = ["chainId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chainId"])
    ]
)
class ChainRuntimeInfoLocal(
    val chainId: String,
    val syncedVersion: Int,
    val remoteVersion: Int,
)
