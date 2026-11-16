package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Exercises the shared independent-wallet recovery contract on JVM SQLite. */
@RunWith(RobolectricTestRunner::class)
class WalletIndependentRevalidationRobolectricTest : WalletIndependentRevalidationContract() {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    override fun newDatabase(): FixtureDatabase {
        val name = "u0-independent-" + UUID.randomUUID().toString()
        val database = JvmMigrationDatabase.createExported(context, name, APP_DATABASE_VERSION)
        database.query("PRAGMA journal_mode=DELETE").use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0).equals("delete", ignoreCase = true))
        }
        return RetainedDatabase(database, context.getDatabasePath(name))
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
