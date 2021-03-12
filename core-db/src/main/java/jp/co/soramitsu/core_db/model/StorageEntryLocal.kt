package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import jp.co.soramitsu.core.model.Node

@Entity(
    tableName = "storage",
    primaryKeys = ["storageKey", "networkType"]
)
class StorageEntryLocal(
    val storageKey: String,
    val networkType: Node.NetworkType,
    val content: String?,
    val runtimeVersion: Int
)
