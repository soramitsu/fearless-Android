package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Exercise the exact later SQL phases before a secret-writing edge commits external preferences.
 * The caller already prepared that edge's public schema. No secret-writing migration, preference
 * store or PRAGMA user_version assignment is reachable from this suffix.
 */
internal object CurrentEndpointUpgradePreflight {
    fun requireSafe(database: SupportSQLiteDatabase, fromVersion: Int) {
        require(fromVersion in setOf(71, 72, 77)) { "Unsupported current endpoint SQL preview start" }
        withRollbackOnlySqlPreview(database) { runSqlSuffix(database, fromVersion) }
    }

    /** Called directly only inside an existing rollback-only historical SQL preview. */
    fun runSqlSuffix(database: SupportSQLiteDatabase, fromVersion: Int) {
        require(fromVersion in setOf(71, 72, 77)) { "Unsupported current endpoint SQL preview start" }
        check(database.inTransaction()) { "Current endpoint SQL preview requires a transaction" }
        if (fromVersion == 71) TonSchemaMigration.migrate(database)
        if (fromVersion <= 72) {
            Migration_72_73.migrate(database)
            Migration_73_74.migrate(database)
            Migration_74_75.migrate(database)
            Migration_75_76.migrate(database)
            WalletIntegritySqlPreparation.prepare(database)
        }
        WalletCustodyMigration.migrate(database)
        PortableWalletReservationMigration.migrate(database)
        PortableWalletOriginReservationMigration.migrate(database)
        LegacyAutoHideNormalizationMigration.migrate(database)
        CurrentEndpointOwnershipMigration.migrate(database)
        EndpointRecoveryDecisionMigration.migrate(database)
        FiatCurrencyIdentityMigration.migrate(database)
    }
}
