package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/** Repair the version71 caches inside Room's transaction, before any external secret write. */
internal object TonUpgradeSqlPreparation {
    fun prepare(database: SupportSQLiteDatabase): RebuildableTableRepairReport {
        check(database.inTransaction()) { "TON cache preparation requires the migration transaction" }
        UpgradeEntryEndpointArchive.beforeDestructiveEdge(database, 71)
        ModernEndpointArchive.capture(database, sourceEdgeVersion = 71)
        return RebuildableTableRepair.repair(
            database = database,
            tables = RELEASED_V71_REBUILDABLE_TABLES,
            foreignKeyCheckLimits = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS
        )
    }
}
