package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.coredb.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class WalletSecretIntegrityQueryPreflightTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val createdDatabases = mutableSetOf<String>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun deleteTestDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
        createdDatabases.clear()
    }

    @Test
    fun canonicalVersion76IdentityQueriesPassWithoutPreferenceMutation() {
        val preferences = TrackingEncryptedPreferences()

        createVersion76Database(CANONICAL_DATABASE).use { database ->
            assertPreferencesUnchanged(preferences) {
                WalletSecretIntegrityQueryPreflight.requireReadable(database)
            }
        }
    }

    @Test
    fun missingAddressBookIndexFailsBeforeAnyPreferenceAccess() {
        val preferences = TrackingEncryptedPreferences()

        createVersion76Database(
            MISSING_ADDRESS_BOOK_INDEX_DATABASE
        ).use { database ->
            database.execSQL(
                "DROP INDEX index_address_book_address_chainId"
            )

            assertThrows(
                WalletPublicIdentityIntegrityException::class.java
            ) {
                assertPreferencesUnchanged(preferences) {
                    WalletSecretIntegrityQueryPreflight.requireReadable(
                        database
                    )
                }
            }
            assertEquals(0, preferences.readCount)
            assertEquals(0, preferences.writeCount)
        }
    }

    @Test
    fun exactInjectedRowAndForeignKeyLimitsAcceptCanonicalRows() {
        val preferences = TrackingEncryptedPreferences()
        val limits = WalletMigrationRowLimits(
            maxWalletRows = 1,
            maxChainAccountRows = 1
        )
        val foreignKeyLimits = foreignKeyCheckLimits(
            "chain_accounts" to 1
        )

        createVersion76Database(EXACT_ROW_LIMIT_DATABASE).use { database ->
            WalletSecretIntegrityQueryPreflight.requireReadable(
                database = database,
                rowLimits = limits,
                foreignKeyCheckLimits = foreignKeyLimits
            )
            assertPreferencesUnchanged(preferences) {
                WalletSecretMigrationPreflight.requireSafeToMutate(
                    database = database,
                    encryptedPreferences = preferences,
                    includeTonPublicKey = true,
                    rowLimits = limits
                )
            }
        }
    }

    @Test
    fun foreignKeyScanLimitPlusOneFailsWithoutPreferenceAccess() {
        val preferences = TrackingEncryptedPreferences()
        val rowLimits = WalletMigrationRowLimits(
            maxWalletRows = 2,
            maxChainAccountRows = 2
        )
        val foreignKeyLimits = foreignKeyCheckLimits(
            "chain_accounts" to 1
        )

        createVersion76Database(
            FOREIGN_KEY_LIMIT_DATABASE
        ).use { database ->
            insertSecondMetaAccount(database)
            insertSecondChainAccount(database)

            assertThrows(WalletPublicIdentityIntegrityException::class.java) {
                assertPreferencesUnchanged(preferences) {
                    WalletSecretIntegrityQueryPreflight.requireReadable(
                        database = database,
                        rowLimits = rowLimits,
                        foreignKeyCheckLimits = foreignKeyLimits
                    )
                }
            }
            assertEquals(0, preferences.readCount)
            assertEquals(0, preferences.writeCount)
        }
    }

    @Test
    fun walletLimitPlusOneFailsBeforeAnyPreferenceAccess() {
        val preferences = TrackingEncryptedPreferences()
        val limits = WalletMigrationRowLimits(
            maxWalletRows = 1,
            maxChainAccountRows = 2
        )

        createVersion76Database(WALLET_ROW_LIMIT_DATABASE).use { database ->
            insertSecondMetaAccount(database)

            assertThrows(WalletPublicIdentityIntegrityException::class.java) {
                WalletSecretMigrationPreflight.requireSafeToMutate(
                    database = database,
                    encryptedPreferences = preferences,
                    includeTonPublicKey = true,
                    rowLimits = limits
                )
            }
            assertEquals(0, preferences.readCount)
            assertEquals(0, preferences.writeCount)
        }
    }

    @Test
    fun chainAccountLimitPlusOneFailsBeforeAnyPreferenceAccess() {
        val preferences = TrackingEncryptedPreferences()
        val limits = WalletMigrationRowLimits(
            maxWalletRows = 2,
            maxChainAccountRows = 1
        )

        createVersion76Database(CHAIN_ROW_LIMIT_DATABASE).use { database ->
            insertSecondMetaAccount(database)
            insertSecondChainAccount(database)

            assertThrows(WalletPublicIdentityIntegrityException::class.java) {
                WalletSecretMigrationPreflight.requireSafeToMutate(
                    database = database,
                    encryptedPreferences = preferences,
                    includeTonPublicKey = true,
                    rowLimits = limits
                )
            }
            assertEquals(0, preferences.readCount)
            assertEquals(0, preferences.writeCount)
        }
    }

    @Test
    fun missingMetaAccountIdentityColumnFailsWithoutPreferenceMutation() {
        val preferences = TrackingEncryptedPreferences()

        createVersion76Database(
            MISSING_META_COLUMN_DATABASE,
            renamedColumn = "meta_accounts" to "tonPublicKey"
        ).use { database ->

            assertThrows(WalletPublicIdentityIntegrityException::class.java) {
                assertPreferencesUnchanged(preferences) {
                    WalletSecretIntegrityQueryPreflight.requireReadable(database)
                }
            }
        }
    }

    @Test
    fun missingChainAccountPublicKeyColumnFailsWithoutPreferenceMutation() {
        val preferences = TrackingEncryptedPreferences()

        createVersion76Database(
            MISSING_CHAIN_PUBLIC_KEY_DATABASE,
            renamedColumn = "chain_accounts" to "publicKey"
        ).use { database ->

            assertThrows(WalletPublicIdentityIntegrityException::class.java) {
                assertPreferencesUnchanged(preferences) {
                    WalletSecretIntegrityQueryPreflight.requireReadable(database)
                }
            }
        }
    }

    @Test
    fun missingChainAccountCryptoTypeColumnFailsWithoutPreferenceMutation() {
        val preferences = TrackingEncryptedPreferences()

        createVersion76Database(
            MISSING_CHAIN_CRYPTO_TYPE_DATABASE,
            renamedColumn = "chain_accounts" to "cryptoType"
        ).use { database ->

            assertThrows(WalletPublicIdentityIntegrityException::class.java) {
                assertPreferencesUnchanged(preferences) {
                    WalletSecretIntegrityQueryPreflight.requireReadable(database)
                }
            }
        }
    }

    @Test
    fun deferredChainAccountWithoutWalletFailsWithoutPreferenceMutation() {
        val preferences = TrackingEncryptedPreferences()

        createVersion76Database(DEFERRED_FOREIGN_KEY_DATABASE).use { database ->
            database.withDeferredChainAccount(
                metaId = SECOND_META_ID,
                chainId = CHAIN_ID
            ) {
                assertThrows(
                    WalletPublicIdentityIntegrityException::class.java
                ) {
                    assertPreferencesUnchanged(preferences) {
                        WalletSecretIntegrityQueryPreflight.requireReadable(
                            database
                        )
                    }
                }
            }
        }
    }

    /**
     * Shipped 4.x builds re-synced the chain registry without networks they
     * had dropped; a per-chain account kept for one of them is the user's
     * and must not stop the upgrade (addendum K10).
     */
    @Test
    fun deferredChainAccountOnDroppedNetworkPassesWithoutPreferenceMutation() {
        val preferences = TrackingEncryptedPreferences()

        createVersion76Database(DROPPED_NETWORK_DATABASE).use { database ->
            database.withDeferredChainAccount(
                metaId = META_ID,
                chainId = MISSING_CHAIN_ID
            ) {
                assertPreferencesUnchanged(preferences) {
                    WalletSecretIntegrityQueryPreflight.requireReadable(
                        database
                    )
                }
            }
        }
    }

    private fun SupportSQLiteDatabase.withDeferredChainAccount(
        metaId: Long,
        chainId: String,
        action: () -> Unit
    ) {
        setForeignKeyConstraintsEnabled(true)
        beginTransaction()
        try {
            execSQL("PRAGMA defer_foreign_keys = ON")
            execSQL(
                """
                INSERT INTO chain_accounts(
                    metaId,
                    chainId,
                    publicKey,
                    accountId,
                    cryptoType,
                    name,
                    initialized
                ) VALUES(?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    metaId,
                    chainId,
                    CHAIN_PUBLIC_KEY,
                    SECOND_CHAIN_ACCOUNT_ID,
                    "SR25519",
                    "Deferred orphan",
                    1
                )
            )
            action()
        } finally {
            endTransaction()
        }
    }

    private fun createVersion76Database(
        databaseName: String,
        renamedColumn: Pair<String, String>? = null
    ): SupportSQLiteDatabase {
        createdDatabases += databaseName
        return helper.createDatabase(databaseName, 76).also { database ->
            insertCanonicalVersion76Rows(database)
            if (renamedColumn != null) {
                val (tableName, columnName) = renamedColumn
                val quotedColumn = "`$columnName`"
                val replacementColumn = "`unavailable${columnName.replaceFirstChar { it.uppercase() }}`"
                val tableSql = database.query(
                    "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?",
                    arrayOf(tableName)
                ).use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getString(0)
                }
                check(tableSql.contains(quotedColumn))
                check(tableSql.contains("`$tableName`"))
                val indexes = database.query(
                    "SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND sql IS NOT NULL",
                    arrayOf(tableName)
                ).use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
                val identityQueries = listOf("meta_accounts", "chain_accounts").associateWith { table ->
                    val projections = database.query("PRAGMA table_info(`$table`)").use { cursor ->
                        val nameColumn = cursor.getColumnIndexOrThrow("name")
                        buildList {
                            while (cursor.moveToNext()) add("quote(`${cursor.getString(nameColumn)}`)")
                        }
                    }
                    "SELECT ${projections.joinToString()} FROM `$table` ORDER BY rowid"
                }
                val rowsBefore = identityQueries.mapValues { (_, sql) ->
                    database.query(sql).use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) add((0 until cursor.columnCount).map(cursor::getString))
                        }
                    }
                }
                val foreignKeysEnabled = database.query("PRAGMA foreign_keys").use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0) != 0
                }
                // API 26 cannot rename a column. Rebuild the same fixture table,
                // retaining its rows, constraints and renamed index definition.
                database.setForeignKeyConstraintsEnabled(false)
                try {
                    database.beginTransaction()
                    try {
                        database.execSQL(
                            tableSql.replaceFirst("`$tableName`", "`renamed_column_fixture`")
                                .replace(quotedColumn, replacementColumn)
                        )
                        database.execSQL("INSERT INTO renamed_column_fixture SELECT * FROM `$tableName`")
                        database.execSQL("DROP TABLE `$tableName`")
                        database.execSQL("ALTER TABLE renamed_column_fixture RENAME TO `$tableName`")
                        indexes.forEach { sql -> database.execSQL(sql.replace(quotedColumn, replacementColumn)) }
                        database.setTransactionSuccessful()
                    } finally {
                        database.endTransaction()
                    }
                } finally {
                    database.setForeignKeyConstraintsEnabled(foreignKeysEnabled)
                }
                identityQueries.forEach { (table, sql) ->
                    val query = if (table == tableName) sql.replace(quotedColumn, replacementColumn) else sql
                    val rowsAfter = database.query(query).use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) add((0 until cursor.columnCount).map(cursor::getString))
                        }
                    }
                    assertEquals("Identity rows changed while renaming $tableName.$columnName", rowsBefore[table], rowsAfter)
                }
                assertEquals(0, database.query("PRAGMA foreign_key_check").use { it.count })
            }
        }
    }

    private fun insertCanonicalVersion76Rows(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL(
            """
            INSERT INTO meta_accounts(
                id,
                substratePublicKey,
                substrateCryptoType,
                substrateAccountId,
                ethereumPublicKey,
                ethereumAddress,
                tonPublicKey,
                name,
                isSelected,
                position,
                isBackedUp,
                googleBackupAddress,
                initialized
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                META_ID,
                SUBSTRATE_PUBLIC_KEY,
                "SR25519",
                SUBSTRATE_ACCOUNT_ID,
                ETHEREUM_PUBLIC_KEY,
                ETHEREUM_ADDRESS,
                TON_PUBLIC_KEY,
                "Query preflight fixture",
                1,
                0,
                1,
                null,
                1
            )
        )
        database.execSQL(
            """
            INSERT INTO chains(
                id,
                name,
                icon,
                prefix,
                isEthereumBased,
                isTestNet,
                hasCrowdloans,
                supportStakingPool,
                isEthereumChain,
                isChainlinkProvider,
                supportNft,
                isUsesAppId,
                ecosystem
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                CHAIN_ID,
                "Query preflight chain",
                "fixture-icon",
                0,
                0,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                "Substrate"
            )
        )
        database.execSQL(
            """
            INSERT INTO chain_accounts(
                metaId,
                chainId,
                publicKey,
                accountId,
                cryptoType,
                name,
                initialized
            ) VALUES(?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                META_ID,
                CHAIN_ID,
                CHAIN_PUBLIC_KEY,
                CHAIN_ACCOUNT_ID,
                "SR25519",
                "Query preflight chain account",
                1
            )
        )
    }

    private fun insertSecondMetaAccount(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO meta_accounts(
                id,
                substratePublicKey,
                substrateCryptoType,
                substrateAccountId,
                ethereumPublicKey,
                ethereumAddress,
                tonPublicKey,
                name,
                isSelected,
                position,
                isBackedUp,
                googleBackupAddress,
                initialized
            )
            SELECT
                ?,
                substratePublicKey,
                substrateCryptoType,
                substrateAccountId,
                ethereumPublicKey,
                ethereumAddress,
                tonPublicKey,
                ?,
                0,
                1,
                isBackedUp,
                googleBackupAddress,
                initialized
            FROM meta_accounts
            WHERE id = ?
            """.trimIndent(),
            arrayOf(
                SECOND_META_ID,
                "Second row-limit wallet",
                META_ID
            )
        )
    }

    private fun insertSecondChainAccount(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO chain_accounts(
                metaId,
                chainId,
                publicKey,
                accountId,
                cryptoType,
                name,
                initialized
            )
            SELECT
                ?,
                chainId,
                publicKey,
                accountId,
                cryptoType,
                ?,
                initialized
            FROM chain_accounts
            WHERE metaId = ?
            """.trimIndent(),
            arrayOf(
                SECOND_META_ID,
                "Second row-limit chain account",
                META_ID
            )
        )
    }

    private fun foreignKeyCheckLimits(
        vararg rowLimits: Pair<String, Int>
    ): MigrationForeignKeyCheckLimits {
        val rowsByTable = WALLET_INTEGRITY_FOREIGN_KEY_CHECK_LIMITS
            .maximumRowsByTable
            .toMutableMap()
        rowLimits.forEach { (tableName, maximumRows) ->
            rowsByTable[tableName] = maximumRows
        }
        return WALLET_INTEGRITY_FOREIGN_KEY_CHECK_LIMITS.copy(
            maximumRowsByTable = rowsByTable
        )
    }

    private fun <T> assertPreferencesUnchanged(
        preferences: TrackingEncryptedPreferences,
        block: () -> T
    ): T {
        val exactSnapshot = preferences.snapshot()
        val writeCount = preferences.writeCount
        return try {
            block()
        } finally {
            assertEquals(exactSnapshot, preferences.snapshot())
            assertEquals(writeCount, preferences.writeCount)
        }
    }

    private class TrackingEncryptedPreferences : EncryptedPreferences {

        private val values = linkedMapOf(
            "unrelated-preference" to "must-remain-exact"
        )

        var writeCount: Int = 0
            private set

        var readCount: Int = 0
            private set

        fun snapshot(): Map<String, String> = values.toMap()

        override fun putEncryptedString(field: String, value: String) {
            writeCount += 1
            values[field] = value
        }

        override fun getDecryptedString(field: String): String? {
            readCount += 1
            return values[field]
        }

        override fun hasKey(field: String): Boolean {
            readCount += 1
            return field in values
        }

        override fun removeKey(field: String) {
            writeCount += 1
            values.remove(field)
        }

        override fun replaceEncryptedStringsDurably(
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>
        ) {
            writeCount += 1
            values.putAll(valuesToPut)
            keysToRemove.forEach(values::remove)
        }

        override fun quarantineEncryptedStringDurably(
            sourceKey: String,
            quarantineKey: String,
            expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean {
            writeCount += 1
            val source = values[sourceKey] ?: return false
            if (!expectedSnapshot.matchesUnencryptedStorageValue(source)) {
                return false
            }
            values[quarantineKey] = source
            values.remove(sourceKey)
            return true
        }

        override fun requireDurableStorageHealthy() = Unit
    }

    private companion object {
        const val META_ID = 7601L
        const val SECOND_META_ID = 7602L
        const val CHAIN_ID = "query-preflight-chain"
        const val MISSING_CHAIN_ID = "missing-query-preflight-chain"

        const val CANONICAL_DATABASE = "query-preflight-v76-canonical"
        const val MISSING_ADDRESS_BOOK_INDEX_DATABASE =
            "query-preflight-v76-missing-address-index"
        const val EXACT_ROW_LIMIT_DATABASE =
            "query-preflight-v76-exact-row-limit"
        const val WALLET_ROW_LIMIT_DATABASE =
            "query-preflight-v76-wallet-row-limit"
        const val CHAIN_ROW_LIMIT_DATABASE =
            "query-preflight-v76-chain-row-limit"
        const val FOREIGN_KEY_LIMIT_DATABASE =
            "query-preflight-v76-foreign-key-limit"
        const val MISSING_META_COLUMN_DATABASE =
            "query-preflight-v76-missing-meta-column"
        const val MISSING_CHAIN_PUBLIC_KEY_DATABASE =
            "query-preflight-v76-missing-chain-public-key"
        const val MISSING_CHAIN_CRYPTO_TYPE_DATABASE =
            "query-preflight-v76-missing-chain-crypto-type"
        const val DEFERRED_FOREIGN_KEY_DATABASE =
            "query-preflight-v76-deferred-foreign-key"
        const val DROPPED_NETWORK_DATABASE =
            "query-preflight-v76-dropped-network"

        val SUBSTRATE_PUBLIC_KEY =
            ByteArray(32) { index -> (index + 1).toByte() }
        val SUBSTRATE_ACCOUNT_ID =
            ByteArray(32) { index -> (index + 33).toByte() }
        val ETHEREUM_PUBLIC_KEY =
            ByteArray(33) { index -> (index + 65).toByte() }
        val ETHEREUM_ADDRESS =
            ByteArray(20) { index -> (index + 98).toByte() }
        val TON_PUBLIC_KEY =
            ByteArray(32) { index -> (index + 118).toByte() }
        val CHAIN_PUBLIC_KEY =
            ByteArray(32) { index -> (index + 11).toByte() }
        val CHAIN_ACCOUNT_ID =
            ByteArray(32) { index -> (index + 43).toByte() }
        val SECOND_CHAIN_ACCOUNT_ID =
            ByteArray(32) { index -> (index + 75).toByte() }
    }
}
