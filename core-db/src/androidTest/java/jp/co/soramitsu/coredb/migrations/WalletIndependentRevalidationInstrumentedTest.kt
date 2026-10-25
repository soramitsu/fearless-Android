package jp.co.soramitsu.coredb.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import org.junit.Rule
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Retains each uniquely named public fixture database for external failure inspection. */
class WalletIndependentRevalidationInstrumentedTest : WalletIndependentRevalidationContract() {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    override fun newDatabase(): FixtureDatabase {
        val name = "u0-independent-" + UUID.randomUUID().toString()
        val database = helper.createDatabase(name, APP_DATABASE_VERSION)
        database.query("PRAGMA journal_mode=DELETE").use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0).equals("delete", ignoreCase = true))
        }
        return RetainedDatabase(database, instrumentation.targetContext.getDatabasePath(name))
    }

    private class RetainedDatabase(
        override val db: SupportSQLiteDatabase,
        private val path: File
    ) : FixtureDatabase {
        override fun rawFileDigest(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            path.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var count = input.read(buffer)
                while (count >= 0) {
                    digest.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        override fun close() = db.close()
    }
}
