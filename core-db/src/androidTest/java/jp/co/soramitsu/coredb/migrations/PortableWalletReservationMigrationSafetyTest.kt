package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class PortableWalletReservationMigrationSafetyTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "portable-wallet-reservation-78-to-79"

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
    fun migrationPreservesWalletsAndFencesReservedIdsWithoutPublishingThem() {
        helper.createDatabase(databaseName, FROM_VERSION).use { database ->
            insertWallet(database, EXISTING_WALLET_ID)
        }

        helper.runMigrationsAndValidate(
            databaseName, TO_VERSION, true, PortableWalletReservationMigration,
        ).use { database ->
            database.query("SELECT COUNT(*) FROM meta_accounts").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM portable_wallet_reservations").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.execSQL(
                "INSERT INTO portable_wallet_reservations(metaId, operationId, afterImageSha256, idSetSha256) " +
                    "VALUES($AUTO_RESERVED_WALLET_ID, 'receive', 'digest', 'ids')"
            )
            assertThrows(RuntimeException::class.java) {
                database.execSQL(
                    """
                    INSERT INTO meta_accounts(
                        substratePublicKey, substrateCryptoType, substrateAccountId,
                        ethereumPublicKey, ethereumAddress, tonPublicKey, name, isSelected,
                        position, isBackedUp, googleBackupAddress, initialized
                    ) VALUES(NULL, NULL, NULL, NULL, NULL, NULL, 'auto', 0, 0, 0, NULL, 0)
                    """.trimIndent()
                )
            }
            database.execSQL(
                "INSERT INTO portable_wallet_reservations(metaId, operationId, afterImageSha256, idSetSha256) " +
                    "VALUES($RESERVED_WALLET_ID, 'receive', 'digest', 'ids')"
            )
            assertThrows(RuntimeException::class.java) { insertWallet(database, RESERVED_WALLET_ID) }
            assertThrows(RuntimeException::class.java) {
                database.execSQL("UPDATE meta_accounts SET id = $RESERVED_WALLET_ID WHERE id = $EXISTING_WALLET_ID")
            }
            database.execSQL(
                "UPDATE portable_wallet_reservations SET state = 1 WHERE metaId = $RESERVED_WALLET_ID"
            )
            assertThrows(RuntimeException::class.java) { insertWallet(database, RESERVED_WALLET_ID) }
            assertThrows(RuntimeException::class.java) {
                database.execSQL("UPDATE meta_accounts SET id = $RESERVED_WALLET_ID WHERE id = $EXISTING_WALLET_ID")
            }
            insertWallet(database, UNRESERVED_WALLET_ID)
            database.query("SELECT COUNT(*) FROM meta_accounts").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM portable_wallet_reservations").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
        }
    }

    private fun insertWallet(database: androidx.sqlite.db.SupportSQLiteDatabase, id: Long) {
        database.execSQL(
            """
            INSERT INTO meta_accounts(
                id, substratePublicKey, substrateCryptoType, substrateAccountId,
                ethereumPublicKey, ethereumAddress, tonPublicKey, name, isSelected,
                position, isBackedUp, googleBackupAddress, initialized
            ) VALUES($id, NULL, NULL, NULL, NULL, NULL, NULL, 'wallet', 0, 0, 0, NULL, 0)
            """.trimIndent()
        )
    }

    private companion object {
        const val FROM_VERSION = 78
        const val TO_VERSION = 79
        const val EXISTING_WALLET_ID = 9L
        const val AUTO_RESERVED_WALLET_ID = 10L
        const val RESERVED_WALLET_ID = 41L
        const val UNRESERVED_WALLET_ID = 42L
    }
}
