package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedWalletPublicIdentityTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val createdDatabases = mutableSetOf<String>()

    @After
    fun deleteTestDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
        createdDatabases.clear()
    }

    @Test
    fun releasedPositiveIntegerWalletIdRemainsReadable() {
        withDatabase(
            databaseName = "bounded-wallet-id-integer",
            idDefinition = "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
            idExpression = VALID_WALLET_ID.toString()
        ) { database ->
            val ids = mutableListOf<Long>()

            forEachBoundedWalletPublicIdentity(
                database = database,
                includeTonPublicKey = true,
                maximumRows = 1
            ) { identity ->
                ids += identity.metaId
            }

            assertEquals(listOf(VALID_WALLET_ID), ids)
        }
    }

    @Test
    fun oversizedBlobWalletIdFailsWithoutVisitingIdentity() {
        withDatabase(
            databaseName = "bounded-wallet-id-oversized-blob",
            idDefinition = "BLOB PRIMARY KEY NOT NULL",
            idExpression = "zeroblob($OVERSIZED_ID_BYTES)"
        ) { database ->
            var visited = false

            assertThrows(WalletPublicIdentityIntegrityException::class.java) {
                forEachBoundedWalletPublicIdentity(
                    database = database,
                    includeTonPublicKey = true,
                    maximumRows = 1
                ) {
                    visited = true
                }
            }

            assertFalse(visited)
        }
    }

    private fun <T> withDatabase(
        databaseName: String,
        idDefinition: String,
        idExpression: String,
        action: (SupportSQLiteDatabase) -> T
    ): T {
        require(
            idDefinition == "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL" ||
                idDefinition == "BLOB PRIMARY KEY NOT NULL"
        )
        require(
            idExpression == VALID_WALLET_ID.toString() ||
                idExpression == "zeroblob($OVERSIZED_ID_BYTES)"
        )
        createdDatabases += databaseName
        context.deleteDatabase(databaseName)
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE meta_accounts(
                        id $idDefinition,
                        substratePublicKey BLOB NOT NULL,
                        substrateCryptoType TEXT NOT NULL,
                        substrateAccountId BLOB NOT NULL,
                        ethereumPublicKey BLOB,
                        ethereumAddress BLOB,
                        tonPublicKey BLOB
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO meta_accounts(
                        id,
                        substratePublicKey,
                        substrateCryptoType,
                        substrateAccountId,
                        ethereumPublicKey,
                        ethereumAddress,
                        tonPublicKey
                    ) VALUES(
                        $idExpression,
                        zeroblob(32),
                        'SR25519',
                        zeroblob(32),
                        NULL,
                        NULL,
                        zeroblob(32)
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                error("Unexpected database upgrade")
            }
        }
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
        return try {
            action(openHelper.writableDatabase)
        } finally {
            openHelper.close()
        }
    }

    private companion object {
        const val VALID_WALLET_ID = 7_601L
        const val OVERSIZED_ID_BYTES = 8 * 1_024 * 1_024
    }
}
