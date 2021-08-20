package jp.co.soramitsu.core_db.model.chain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import androidx.room.Index

@Entity(
    tableName = "chain_assets",
    primaryKeys = ["id", "chainId"],
    foreignKeys = [
        ForeignKey(
            entity = ChainLocal::class,
            parentColumns = ["id"],
            childColumns = ["chainId"],
            onDelete = CASCADE
        )
    ],
    indices = [
        Index(value = ["chainId"])
    ]
)
class ChainAssetLocal(
    val id: Int,
    val chainId: String,
    val name: String?,
    val symbol: String,
    val precision: Int
)
