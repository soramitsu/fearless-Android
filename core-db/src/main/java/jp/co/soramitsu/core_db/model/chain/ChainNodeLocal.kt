package jp.co.soramitsu.core_db.model.chain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "chain_nodes",
    primaryKeys = ["chainId", "url"],
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
class ChainNodeLocal(
    val chainId: String,
    val url: String,
    val name: String
)
