package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Read-only admission of the original endpoint table as an optional archive.
 * The table's values are opaque: this does not grant them a chain or active-node identity.
 */
internal object LegacyNodeArchive {
    fun requireSafe(database: SupportSQLiteDatabase): Boolean {
        database.query(
            "SELECT 1 FROM sqlite_temp_master WHERE name = 'nodes' COLLATE NOCASE " +
                "OR tbl_name = 'nodes' COLLATE NOCASE LIMIT 1"
        ).use { cursor ->
            check(!cursor.moveToFirst()) { "Legacy node archive has a temporary namespace collision" }
        }
        val projections = listOf("type", "name", "tbl_name").joinToString { column ->
            "CASE WHEN typeof($column) = 'text' AND length(CAST($column AS BLOB)) <= 256 " +
                "THEN $column ELSE NULL END AS `$column`"
        }
        val present = database.query(
            "SELECT $projections FROM main.sqlite_master WHERE name = 'nodes' COLLATE NOCASE " +
                "OR tbl_name = 'nodes' COLLATE NOCASE ORDER BY rowid LIMIT 2"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use false
            check(
                cursor.requireText("type") == "table" &&
                    cursor.requireText("name") == "nodes" &&
                    cursor.requireText("tbl_name") == "nodes"
            ) { "Legacy node archive is not the exact named main table" }
            check(!cursor.moveToNext()) { "Legacy node archive has extra owned schema objects" }
            true
        }
        if (!present) return false
        database.query("PRAGMA main.foreign_key_list('nodes')").use { cursor ->
            check(!cursor.moveToFirst()) { "Legacy node archive declares unsupported foreign keys" }
        }
        database.query("PRAGMA main.index_list('nodes')").use { cursor ->
            check(!cursor.moveToFirst()) { "Legacy node archive has unsupported indexes" }
        }
        check(releasedRoomTableShape(database, SOURCE_SCHEMA) == ReleasedRoomTableShape.MATCHES) {
            "Legacy node archive has an unsupported released schema"
        }
        return true
    }

    private fun Cursor.requireText(column: String): String {
        val index = getColumnIndexOrThrow(column)
        check(getType(index) == Cursor.FIELD_TYPE_STRING) { "Legacy node archive has unsafe $column metadata" }
        return getString(index)
    }

    private val SOURCE_SCHEMA = ReleasedRoomTableSpec(
        name = "nodes",
        columns = listOf(
            ReleasedRoomColumnSpec("id", "INTEGER", notNull = true, primaryKeyPosition = 1),
            ReleasedRoomColumnSpec("name", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("link", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("networkType", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec("isDefault", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec("isActive", "INTEGER", notNull = true)
        ),
        alternateColumnOrderCohorts = listOf(
            // Exact fresh 1.9.15/2.0.0 order. No declared defaults.
            ReleasedRoomColumnOrderCohort(
                listOf("name", "link", "networkType", "isDefault", "isActive", "id")
            ),
            // DB10's id-first source plus the actual 18 -> 19 appended column.
            ReleasedRoomColumnOrderCohort(
                listOf("id", "name", "link", "networkType", "isDefault", "isActive"),
                explicitDefaultValues = mapOf("isActive" to "0")
            ),
            // Source-derived DB9 has id last before the 18 -> 19 append.
            ReleasedRoomColumnOrderCohort(
                listOf("name", "link", "networkType", "isDefault", "id", "isActive"),
                explicitDefaultValues = mapOf("isActive" to "0")
            )
        )
    )
}
