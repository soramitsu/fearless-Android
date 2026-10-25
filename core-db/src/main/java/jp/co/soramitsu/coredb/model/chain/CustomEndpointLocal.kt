package jp.co.soramitsu.coredb.model.chain

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User ownership survives catalog parent deletion. No field grants network identity or activation.
 * Snapshot adoption copies TEXT bytes inside SQLite; recovery reads must use bounded raw projections,
 * not load this entire entity through a CursorWindow. Deleted records retain an explicit tombstone.
 */
@Entity(tableName = "custom_endpoint_records", indices = [Index(value = ["chainId", "url"], unique = true)])
data class CustomEndpointLocal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val revision: Long = 1,
    val chainId: String,
    val url: String,
    val name: String,
    val attached: Boolean,
    val deleted: Boolean = false,
    val originalSnapshotRowId: Long? = null
) {
    override fun toString(): String = "CustomEndpointLocal(id=$id, revision=$revision, attached=$attached, deleted=$deleted)"
}
