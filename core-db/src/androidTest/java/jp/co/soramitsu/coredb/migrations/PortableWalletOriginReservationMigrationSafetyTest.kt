package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PortableWalletOriginReservationMigrationSafetyTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "portable-wallet-origin-reservation-79-to-80"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPreservesExistingWalletAndPendingCohortWithoutGuessingOrigin() {
        helper.createDatabase(databaseName, 79).use { database ->
            database.execSQL(
                "INSERT INTO meta_accounts(id, name, isSelected, position, isBackedUp, initialized) " +
                    "VALUES(9, 'existing', 1, 0, 0, 1)"
            )
            database.execSQL(
                "INSERT INTO portable_wallet_reservations(metaId, operationId, afterImageSha256, idSetSha256) " +
                    "VALUES(41, 'receive', 'digest', 'ids')"
            )
        }

        helper.runMigrationsAndValidate(
            databaseName, 80, true, PortableWalletOriginReservationMigration,
        ).use { database ->
            database.query("SELECT id, name FROM meta_accounts").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(9L, cursor.getLong(0))
                assertEquals("existing", cursor.getString(1))
                assertEquals(false, cursor.moveToNext())
            }
            database.query(
                "SELECT metaId, state, portableIdHex, sourcePosition " +
                    "FROM portable_wallet_reservations WHERE metaId = 41"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(41L, cursor.getLong(0))
                assertEquals(0, cursor.getInt(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
            }
            assertThrows(RuntimeException::class.java) {
                database.execSQL(
                    "INSERT INTO meta_accounts(id, name, isSelected, position, isBackedUp, initialized) " +
                        "VALUES(41, 'collision', 0, 1, 0, 1)"
                )
            }
            database.execSQL(
                "UPDATE portable_wallet_reservations " +
                    "SET portableIdHex = '0102030405060708090a0b0c0d0e0f10', sourcePosition = 4294967295 " +
                    "WHERE metaId = 41"
            )
            database.execSQL(
                "INSERT INTO portable_wallet_reservations(" +
                    "metaId, operationId, afterImageSha256, idSetSha256, portableIdHex, sourcePosition) " +
                    "VALUES(42, 'other', 'digest', 'ids', '1112131415161718191a1b1c1d1e1f20', 0)"
            )
            // An abandoned ID remains unusable, but a retry may restore the same source wallet
            // under a new local ID after the original encrypted journal has been abandoned.
            database.execSQL("UPDATE portable_wallet_reservations SET state = 1 WHERE metaId = 41")
            database.execSQL(
                "INSERT INTO portable_wallet_reservations(" +
                    "metaId, operationId, afterImageSha256, idSetSha256, portableIdHex, sourcePosition) " +
                    "VALUES(43, 'retry', 'digest', 'ids', '0102030405060708090a0b0c0d0e0f10', 4294967295)"
            )
            database.query("SELECT COUNT(*) FROM portable_wallet_reservations").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
        }
    }
}
