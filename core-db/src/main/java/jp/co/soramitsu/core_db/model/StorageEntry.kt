package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "storage")
class StorageEntry(
    @PrimaryKey val storageKey: String,
    val content: String?,
    val runtimeVersion: Int
)