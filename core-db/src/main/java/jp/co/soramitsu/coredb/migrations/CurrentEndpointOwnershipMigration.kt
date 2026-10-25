package jp.co.soramitsu.coredb.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Preserve the exact current source independently of the earliest archive, then establish durable
 * custom ownership. Raw anomalies stay in the snapshot; they never gain operational authority.
 * Room owns this transaction. The same routine must run in rollback-only previews before earlier
 * preference writes, without changing PRAGMA user_version or invoking secret-writing migrations.
 */
internal object CurrentEndpointOwnershipMigration : Migration(81, 82) {
    override fun migrate(db: SupportSQLiteDatabase) {
        check(db.inTransaction()) { "Current endpoint adoption requires the migration transaction" }
        requireTargetsAbsent(db)
        ModernEndpointArchive.requireSafe(db)
        UpgradeEntryEndpointArchive.requireSafeForSource(db)
        val captured = CurrentEndpointArchive.capture(db, sourceEdgeVersion = 81)
        db.execSQL(
            "CREATE TABLE `custom_endpoint_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`revision` INTEGER NOT NULL, `chainId` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `attached` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, " +
                "`originalSnapshotRowId` INTEGER)"
        )
        db.execSQL("CREATE UNIQUE INDEX `index_custom_endpoint_records_chainId_url` ON `custom_endpoint_records` (`chainId`, `url`)")
        if (captured) {
            // Group once, then use integer lookups; never rescan all raw rows for every endpoint.
            db.execSQL("CREATE TEMP TABLE current_endpoint_adoption_keys_v1 (source_rowid INTEGER PRIMARY KEY, operational INTEGER NOT NULL DEFAULT 0)")
            db.execSQL(
                "INSERT INTO temp.current_endpoint_adoption_keys_v1 (source_rowid) " +
                    "SELECT MIN(source_rowid) FROM current_endpoint_archive_v1 " +
                    "WHERE typeof(chainId) = 'text' AND typeof(url) = 'text' " +
                    "GROUP BY chainId COLLATE BINARY, url COLLATE BINARY HAVING COUNT(*) = 1"
            )
            CurrentEndpointTextAdmission.markOrdinaryUtf8(db)
            // SQLite copies the exact TEXT bytes, including values a bounded UI reader may only
            // expose as opaque bytes. No endpoint payload is materialized through a CursorWindow.
            db.execSQL(
                "INSERT INTO custom_endpoint_records (revision, chainId, url, name, attached, deleted, originalSnapshotRowId) " +
                    "SELECT 1, s.chainId, s.url, s.name, (s.source_rowid IN (SELECT source_rowid FROM temp.current_endpoint_adoption_keys_v1 WHERE operational = 1) AND EXISTS (SELECT 1 FROM chains c WHERE c.id = s.chainId)), 0, s.source_rowid " +
                    "FROM current_endpoint_archive_v1 s WHERE $ORDINARY_UNAMBIGUOUS AND s.isDefault = 0 ORDER BY s.source_rowid"
            )
            // The exact original row identities, schema and values have already been captured.
            // Rebuild only this operational projection; parents and wallet rows remain untouched.
            db.execSQL("DROP TABLE chain_nodes")
        }
        db.execSQL(
            "CREATE TABLE `chain_nodes` (`chainId` TEXT NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`isActive` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, PRIMARY KEY(`chainId`, `url`), " +
                "FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX `index_chain_nodes_chainId` ON `chain_nodes` (`chainId`)")
        if (captured) {
            db.execSQL(
                "INSERT INTO chain_nodes (chainId, url, name, isActive, isDefault) " +
                    "SELECT s.chainId, s.url, s.name, s.isActive, s.isDefault FROM current_endpoint_archive_v1 s " +
                    "WHERE $ORDINARY_UNAMBIGUOUS AND s.source_rowid IN (SELECT source_rowid FROM temp.current_endpoint_adoption_keys_v1 WHERE operational = 1) AND EXISTS (SELECT 1 FROM chains c WHERE c.id = s.chainId)"
            )
            db.execSQL("DROP TABLE temp.current_endpoint_adoption_keys_v1")
        }
    }

    private fun requireTargetsAbsent(db: SupportSQLiteDatabase) {
        val names = listOf(
            "custom_endpoint_records", "index_custom_endpoint_records_chainId_url", "current_endpoint_adoption_keys_v1",
            "current_endpoint_archive_v1", "current_endpoint_archive_header_v1", "current_endpoint_archive_schema_v1"
        )
        val predicate = names.joinToString(" OR ") { "name = '$it' COLLATE NOCASE OR tbl_name = '$it' COLLATE NOCASE" }
        for (catalog in listOf("main.sqlite_master", "sqlite_temp_master")) {
            db.query("SELECT 1 FROM $catalog WHERE $predicate LIMIT 1").use {
                check(!it.moveToFirst()) { "Current endpoint ownership target already exists; original data is unchanged" }
            }
        }
    }

    // Duplicate raw keys remain unresolved in the snapshot; no arbitrary winning label is selected.
    private const val ORDINARY_UNAMBIGUOUS =
        "typeof(s.chainId) = 'text' AND typeof(s.url) = 'text' AND typeof(s.name) = 'text' " +
            "AND typeof(s.isActive) = 'integer' AND s.isActive IN (0,1) " +
            "AND typeof(s.isDefault) = 'integer' AND s.isDefault IN (0,1) " +
            "AND s.source_rowid IN (SELECT source_rowid FROM temp.current_endpoint_adoption_keys_v1)"
}
