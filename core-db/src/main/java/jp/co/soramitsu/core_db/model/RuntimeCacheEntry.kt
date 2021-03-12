package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runtimeCache")
data class RuntimeCacheEntry(
    @PrimaryKey val networkName: String,
    val latestKnownVersion: Int,
    val latestAppliedVersion: Int,
    val typesVersion: Int
) {
    companion object {
        fun default(networkName: String) = RuntimeCacheEntry(
            networkName = networkName,
            latestKnownVersion = 0,
            latestAppliedVersion = 0,
            typesVersion = 0
        )
    }
}
