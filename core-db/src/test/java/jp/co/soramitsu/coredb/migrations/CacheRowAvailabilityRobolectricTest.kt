package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs the shared migration contract against the frozen released SQLite database. */
@RunWith(RobolectricTestRunner::class)
class CacheRowAvailabilityRobolectricTest : CacheRowAvailabilityContract() {
    override fun fixture(
        name: String,
        mutate: (SupportSQLiteDatabase) -> Unit,
        rollback: Boolean,
        transaction: Boolean,
        check: (SupportSQLiteDatabase) -> Unit
    ) = JvmReleasedSqlFixture.execute(name, mutate, rollback, transaction, check)
}
