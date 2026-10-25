package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Keep the input to this upgrade's first destructive endpoint edge independently of the earliest
 * historical archive. This raw snapshot never confers ownership, attachment or active selection.
 * Original user_version remains fixed until Room commits the entire upgrade transaction.
 */
internal object UpgradeEntryEndpointArchive {
    private val format = EndpointArchiveFormat.UPGRADE_ENTRY
    private val edges = listOf(39, 41, 45, 53, 71, 76)
    val schemaObjects: Set<ReleasedRoomSchemaObjectSpec> get() = format.schemaObjects

    /** First entry requires an unused namespace; later edges can only validate the same capture. */
    fun beforeDestructiveEdge(database: SupportSQLiteDatabase, sourceEdgeVersion: Int) {
        require(sourceEdgeVersion in edges) { "Unsupported upgrade-entry endpoint edge" }
        val first = firstEdge(database.version)
        if (sourceEdgeVersion == first) {
            check(!format.requireSafe(database)) { "Upgrade-entry endpoint snapshot already exists at first edge" }
            format.capture(database, sourceEdgeVersion)
        } else {
            requireSafeForSource(database)
        }
    }

    /** Present data must belong to this original source version and its deterministic first edge. */
    fun requireSafeForSource(database: SupportSQLiteDatabase): Boolean {
        if (!format.requireSafe(database)) return false
        val first = checkNotNull(firstEdge(database.version)) {
            "Upgrade-entry endpoint snapshot is not valid for this source version"
        }
        database.query(
            "SELECT source_edge_version,source_user_version FROM upgrade_entry_endpoint_archive_header_v1 WHERE id=1"
        ).use { cursor ->
            check(cursor.moveToFirst() && cursor.getType(0) == Cursor.FIELD_TYPE_INTEGER &&
                cursor.getType(1) == Cursor.FIELD_TYPE_INTEGER && cursor.getLong(0) == first.toLong() &&
                cursor.getLong(1) == database.version.toLong() && !cursor.moveToNext()) {
                "Upgrade-entry endpoint snapshot source provenance differs"
            }
        }
        return true
    }

    /** Earlier completed upgrades captured source54..71 at76, before the71 repair existed. */
    internal fun admitsStoredCapture(sourceVersion: Int, sourceEdgeVersion: Int): Boolean =
        firstEdge(sourceVersion) == sourceEdgeVersion ||
            (sourceVersion in 54..71 && sourceEdgeVersion == 76)

    /** The first destructive edge encountered by a source upgrade; modern77+ has none. */
    internal fun firstEdge(sourceVersion: Int): Int? {
        require(sourceVersion >= 0) { "Invalid upgrade-entry source version" }
        return edges.firstOrNull { it >= sourceVersion }
    }
}
