package jp.co.soramitsu.coredb.model

import androidx.room.Entity

@Entity(
    tableName = "storage",
    primaryKeys = ["chainId", "storageKey"]
)
class StorageEntryLocal(
    val storageKey: String,
    val content: String?,
    val chainId: String
)
