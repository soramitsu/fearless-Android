package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WalletCustodyMigrationSafetyTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "wallet-custody-77-to-78"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun existingWatchShapeAndSignedShapeBothRemainUnknown() {
        helper.createDatabase(databaseName, FROM_VERSION).use { database ->
            database.execSQL(
                """
                INSERT INTO meta_accounts(
                    id, substratePublicKey, substrateCryptoType, substrateAccountId,
                    ethereumPublicKey, ethereumAddress, tonPublicKey, name, isSelected,
                    position, isBackedUp, googleBackupAddress, initialized
                ) VALUES(1, X'0101', 'SR25519', X'0101', NULL, NULL, NULL,
                    'historical watch-shaped row', 1, 0, 0, NULL, 0)
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO meta_accounts(
                    id, substratePublicKey, substrateCryptoType, substrateAccountId,
                    ethereumPublicKey, ethereumAddress, tonPublicKey, name, isSelected,
                    position, isBackedUp, googleBackupAddress, initialized
                ) VALUES(2, NULL, NULL, NULL, X'0202', X'0303', NULL,
                    'historical signer-shaped row', 0, 1, 0, NULL, 1)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(databaseName, TO_VERSION, true, WalletCustodyMigration).use { database ->
            database.query("SELECT COUNT(*) FROM meta_accounts").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM wallet_custody").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val FROM_VERSION = 77
        const val TO_VERSION = 78
    }
}
