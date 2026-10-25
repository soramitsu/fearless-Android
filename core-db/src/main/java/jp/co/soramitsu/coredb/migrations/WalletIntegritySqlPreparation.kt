package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/** The shared SQL-only 76-to-77 prefix; it never reads or writes encrypted preferences. */
internal object WalletIntegritySqlPreparation {
    fun prepare(db: SupportSQLiteDatabase) {
        UpgradeEntryEndpointArchive.beforeDestructiveEdge(db, 76)
        ModernEndpointArchive.capture(db, sourceEdgeVersion = 76)
        RebuildableTableRepair.repair(db)
    }
}
