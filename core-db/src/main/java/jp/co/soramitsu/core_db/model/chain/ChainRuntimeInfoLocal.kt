package jp.co.soramitsu.core_db.model.chain

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "chain_runtimes",
    primaryKeys = ["chainId"],
    indices = [
        Index(value = ["chainId"])
    ]
)
class ChainRuntimeInfoLocal(
    val chainId: String,
    val syncedVersion: Int,
    val remoteVersion: Int,
)
