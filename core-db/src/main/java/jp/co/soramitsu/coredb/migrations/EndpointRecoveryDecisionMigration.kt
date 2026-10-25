package jp.co.soramitsu.coredb.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds an empty offline-intent table; no endpoint is adopted, attached or selected. */
internal object EndpointRecoveryDecisionMigration : Migration(82, 83) {
    const val CREATE = "CREATE TABLE `endpoint_recovery_decisions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`revision` INTEGER NOT NULL, `sourceKind` TEXT NOT NULL, `sourceRowId` INTEGER NOT NULL, `sourceRevision` INTEGER, " +
        "`sourceFingerprint` TEXT NOT NULL, `candidateName` TEXT NOT NULL, `candidateUrl` TEXT NOT NULL, " +
        "`targetChainId` TEXT NOT NULL, `targetIdentityFingerprint` TEXT NOT NULL, `targetFamily` TEXT NOT NULL, " +
        "`targetExpectedGenesis` TEXT, `state` TEXT NOT NULL)"
    const val CREATE_INDEX = "CREATE UNIQUE INDEX `index_endpoint_recovery_decisions_sourceKind_sourceRowId` " +
        "ON `endpoint_recovery_decisions` (`sourceKind`, `sourceRowId`)"
    internal const val NAMESPACE = "name='endpoint_recovery_decisions' COLLATE NOCASE OR " +
        "tbl_name='endpoint_recovery_decisions' COLLATE NOCASE OR " +
        "name='index_endpoint_recovery_decisions_sourceKind_sourceRowId' COLLATE NOCASE OR " +
        "tbl_name='index_endpoint_recovery_decisions_sourceKind_sourceRowId' COLLATE NOCASE"

    override fun migrate(db: SupportSQLiteDatabase) {
        check(db.inTransaction()) { "Endpoint decision migration requires a transaction" }
        for (schema in listOf("main", "temp")) {
            db.query("SELECT 1 FROM $schema.sqlite_master WHERE $NAMESPACE LIMIT 1").use {
                check(!it.moveToFirst()) { "Endpoint decision target namespace already exists" }
            }
        }
        db.execSQL(CREATE)
        db.execSQL(CREATE_INDEX)
    }
}
