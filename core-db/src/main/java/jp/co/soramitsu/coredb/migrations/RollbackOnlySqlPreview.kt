package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Runs SQL preparation inside the caller's upgrade transaction and restores
 * its savepoint on both success and failure. The action must not write to
 * preferences or any other storage outside this SQLite connection.
 *
 * A failed rollback is never followed by RELEASE: the caller must fail its
 * outer transaction. Preserve the original failure if cleanup also fails.
 */
internal fun <T> withRollbackOnlySqlPreview(
    database: SupportSQLiteDatabase,
    action: () -> T
): T {
    check(database.inTransaction()) {
        "SQL upgrade preview requires an existing transaction"
    }
    database.execSQL("SAVEPOINT fearless_upgrade_preview")
    var actionFailure: Throwable? = null
    try {
        return action()
    } catch (failure: Throwable) {
        actionFailure = failure
        throw failure
    } finally {
        try {
            // API 16-27 classifies every leading ROL as a whole-transaction
            // abort. AOSP's API 28 DatabaseUtils documents the leading ';'
            // workaround so SQLite receives the actual savepoint statement.
            database.execSQL(";ROLLBACK TO SAVEPOINT fearless_upgrade_preview")
            database.execSQL("RELEASE SAVEPOINT fearless_upgrade_preview")
        } catch (cleanupFailure: Throwable) {
            val original = actionFailure
            if (original == null) throw cleanupFailure
            original.addSuppressed(cleanupFailure)
        }
    }
}
