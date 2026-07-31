package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV374DatabaseFixture
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LargeClass", "MagicNumber", "NestedBlockDepth")
class TonUpgradeSqlPreflightTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun deleteTestDatabases() {
        DATABASE_NAMES.forEach { databaseName ->
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun canonicalReleasedVersion71SchemaPassesWithoutPreferenceMutation() {
        installFixture(CANONICAL_DATABASE)
        val preferenceProbe = EncryptedPreferenceMutationProbe()
        val exactBefore = preferenceProbe.rawSnapshot()

        withLogicalVersion71Schema(CANONICAL_DATABASE) { database ->
            TonUpgradeSqlPreflight.requireSafeToMutate(database)
        }

        assertRawSnapshotsEqual(exactBefore, preferenceProbe.rawSnapshot())
    }

    @Test
    fun logicalVersion71SchemaWithOlderTransactionMetadataStillPasses() {
        installFixture(OLDER_TRANSACTION_METADATA_DATABASE)
        mutateRawDatabase(OLDER_TRANSACTION_METADATA_DATABASE) {
            version = 31
            execSQL(
                "UPDATE room_master_table SET identity_hash = ? WHERE id = 42",
                arrayOf("historical-direct-upgrade-identity")
            )
        }
        val preferenceProbe = EncryptedPreferenceMutationProbe()
        val exactBefore = preferenceProbe.rawSnapshot()

        withLogicalVersion71Schema(
            databaseName = OLDER_TRANSACTION_METADATA_DATABASE,
            openVersion = 31
        ) { database ->
            assertEquals(31, database.version)
            TonUpgradeSqlPreflight.requireSafeToMutate(database)
        }

        assertRawSnapshotsEqual(exactBefore, preferenceProbe.rawSnapshot())
    }

    @Test
    fun compatiblePreexistingTonConnectionPassesWithoutPreferenceMutation() {
        installFixture(COMPATIBLE_TON_CONNECTION_DATABASE)
        mutateRawDatabase(COMPATIBLE_TON_CONNECTION_DATABASE) {
            execSQL(COMPATIBLE_TON_CONNECTION_SQL)
        }
        val preferenceProbe = EncryptedPreferenceMutationProbe()
        val exactBefore = preferenceProbe.rawSnapshot()

        withLogicalVersion71Schema(
            COMPATIBLE_TON_CONNECTION_DATABASE
        ) { database ->
            TonUpgradeSqlPreflight.requireSafeToMutate(database)
        }

        assertRawSnapshotsEqual(exactBefore, preferenceProbe.rawSnapshot())
    }

    @Test
    fun malformedVersion71ColumnDefinitionFailsBeforePreferenceMutation() {
        installFixture(MALFORMED_COLUMN_DATABASE)
        replaceTableSchemaSql(
            databaseName = MALFORMED_COLUMN_DATABASE,
            tableName = "chains",
            oldDefinition = "`isEthereumBased` INTEGER NOT NULL",
            newDefinition = "`isEthereumBased` TEXT NOT NULL"
        )

        assertRejectedBeforePreferenceMutation(
            databaseName = MALFORMED_COLUMN_DATABASE,
            expectedMessage = "isEthereumBased has an incompatible definition"
        )
    }

    @Test
    fun missingRetainedAddressBookIndexFailsBeforePreferenceMutation() {
        installFixture(MISSING_ADDRESS_BOOK_INDEX_DATABASE)
        mutateRawDatabase(MISSING_ADDRESS_BOOK_INDEX_DATABASE) {
            execSQL("DROP INDEX index_address_book_address_chainId")
        }

        assertRejectedBeforePreferenceMutation(
            databaseName = MISSING_ADDRESS_BOOK_INDEX_DATABASE,
            expectedMessage =
            "missing, extra, or incompatible SQLite schema objects"
        )
    }

    @Test
    fun alteredRetainedAddressBookIndexFailsBeforePreferenceMutation() {
        installFixture(ALTERED_ADDRESS_BOOK_INDEX_DATABASE)
        mutateRawDatabase(ALTERED_ADDRESS_BOOK_INDEX_DATABASE) {
            execSQL("DROP INDEX index_address_book_address_chainId")
            execSQL(
                "CREATE UNIQUE INDEX index_address_book_address_chainId " +
                    "ON address_book(chainId, address)"
            )
        }

        assertRejectedBeforePreferenceMutation(
            databaseName = ALTERED_ADDRESS_BOOK_INDEX_DATABASE,
            expectedMessage =
            "address_book has incompatible explicit indexes"
        )
    }

    @Test
    fun descendingRetainedAddressBookIndexFailsBeforePreferenceMutation() {
        installFixture(DESCENDING_ADDRESS_BOOK_INDEX_DATABASE)
        mutateRawDatabase(DESCENDING_ADDRESS_BOOK_INDEX_DATABASE) {
            execSQL("DROP INDEX index_address_book_address_chainId")
            execSQL(
                "CREATE UNIQUE INDEX index_address_book_address_chainId " +
                    "ON address_book(address DESC, chainId)"
            )
        }

        assertRejectedBeforePreferenceMutation(
            databaseName = DESCENDING_ADDRESS_BOOK_INDEX_DATABASE,
            expectedMessage = "incompatible indexed-column metadata"
        )
    }

    @Test
    fun alteredRetainedColumnDefaultFailsBeforePreferenceMutation() {
        installFixture(ALTERED_RETAINED_DEFAULT_DATABASE)
        replaceTableSchemaSql(
            databaseName = ALTERED_RETAINED_DEFAULT_DATABASE,
            tableName = "address_book",
            oldDefinition = "`created` INTEGER NOT NULL",
            newDefinition = "`created` INTEGER NOT NULL DEFAULT 7"
        )

        assertRejectedBeforePreferenceMutation(
            databaseName = ALTERED_RETAINED_DEFAULT_DATABASE,
            expectedMessage =
            "address_book has incompatible ordered column definitions"
        )
    }

    @Test
    fun retainedCheckClauseFailsBeforePreferenceMutation() {
        installFixture(RETAINED_CHECK_CLAUSE_DATABASE)
        replaceTableSchemaSql(
            databaseName = RETAINED_CHECK_CLAUSE_DATABASE,
            tableName = "address_book",
            oldDefinition = "`created` INTEGER NOT NULL",
            newDefinition =
            "`created` INTEGER NOT NULL CHECK (`created` >= 0)"
        )

        assertRejectedBeforePreferenceMutation(
            databaseName = RETAINED_CHECK_CLAUSE_DATABASE,
            expectedMessage = "unsupported released-schema clause CHECK"
        )
    }

    @Test
    fun additionalTriggerFailsBeforePreferenceMutation() {
        installFixture(ADDITIONAL_TRIGGER_DATABASE)
        mutateRawDatabase(ADDITIONAL_TRIGGER_DATABASE) {
            execSQL(
                """
                CREATE TRIGGER reject_address_book_insert
                BEFORE INSERT ON address_book
                BEGIN
                    SELECT RAISE(ABORT, 'blocked');
                END
                """.trimIndent()
            )
        }

        assertRejectedBeforePreferenceMutation(
            databaseName = ADDITIONAL_TRIGGER_DATABASE,
            expectedMessage =
            "missing, extra, or incompatible SQLite schema objects"
        )
    }

    @Test
    fun releasedAlterTableDefaultVariantsPassWithoutPreferenceMutation() {
        installFixture(RELEASED_DEFAULT_VARIANTS_DATABASE)
        replaceTableSchemaSql(
            databaseName = RELEASED_DEFAULT_VARIANTS_DATABASE,
            tableName = "operations",
            oldDefinition = "`liquidityFee` TEXT",
            newDefinition = "`liquidityFee` TEXT DEFAULT NULL"
        )
        replaceTableSchemaSql(
            databaseName = RELEASED_DEFAULT_VARIANTS_DATABASE,
            tableName = "assets",
            oldDefinition = "`sortIndex` INTEGER NOT NULL",
            newDefinition = "`sortIndex` INTEGER NOT NULL DEFAULT 0"
        )
        val preferenceProbe = EncryptedPreferenceMutationProbe()
        val exactBefore = preferenceProbe.rawSnapshot()

        withLogicalVersion71Schema(
            RELEASED_DEFAULT_VARIANTS_DATABASE
        ) { database ->
            TonUpgradeSqlPreflight.requireSafeToMutate(database)
        }

        assertRawSnapshotsEqual(exactBefore, preferenceProbe.rawSnapshot())
    }

    @Test
    fun unshippedMetaAccountOrderDefaultCrossProductFailsClosed() {
        installFixture(UNSHIPPED_META_DEFAULT_DATABASE)
        replaceTableSchemaSql(
            databaseName = UNSHIPPED_META_DEFAULT_DATABASE,
            tableName = "meta_accounts",
            oldDefinition = "`initialized` INTEGER NOT NULL",
            newDefinition = "`initialized` INTEGER NOT NULL DEFAULT 0"
        )

        assertRejectedBeforePreferenceMutation(
            databaseName = UNSHIPPED_META_DEFAULT_DATABASE,
            expectedMessage =
            "meta_accounts has incompatible ordered column definitions"
        )
    }

    @Test
    fun unshippedChainOrderDefaultCrossProductFailsClosed() {
        installFixture(UNSHIPPED_CHAIN_DEFAULT_DATABASE)
        replaceTableSchemaSql(
            databaseName = UNSHIPPED_CHAIN_DEFAULT_DATABASE,
            tableName = "chains",
            oldDefinition = "`remoteAssetsSource` TEXT",
            newDefinition = "`remoteAssetsSource` TEXT DEFAULT NULL"
        )

        assertRejectedBeforePreferenceMutation(
            databaseName = UNSHIPPED_CHAIN_DEFAULT_DATABASE,
            expectedMessage =
            "chains has incompatible ordered column definitions"
        )
    }

    @Test
    fun missingRequiredVersion71ColumnFailsBeforePreferenceMutation() {
        installFixture(MISSING_COLUMN_DATABASE)
        replaceTableSchemaSql(
            databaseName = MISSING_COLUMN_DATABASE,
            tableName = "chain_accounts",
            oldDefinition = "`publicKey`",
            newDefinition = "`unavailablePublicKey`"
        )

        assertRejectedBeforePreferenceMutation(
            databaseName = MISSING_COLUMN_DATABASE,
            expectedMessage = "missing required column publicKey"
        )
    }

    @Test
    fun everyAlreadyAddedFutureColumnFailsBeforePreferenceMutation() {
        FUTURE_COLUMN_CASES.forEach { case ->
            installFixture(case.databaseName)
            mutateRawDatabase(case.databaseName) {
                execSQL(
                    "ALTER TABLE `${case.tableName}` " +
                        "ADD COLUMN `${case.columnName}` TEXT"
                )
            }

            assertRejectedBeforePreferenceMutation(
                databaseName = case.databaseName,
                expectedMessage =
                "already contains ${case.columnName}"
            )
        }
    }

    @Test
    fun incompatibleTonConnectionFailsBeforePreferenceMutation() {
        installFixture(INCOMPATIBLE_TON_CONNECTION_DATABASE)
        mutateRawDatabase(INCOMPATIBLE_TON_CONNECTION_DATABASE) {
            execSQL(
                """
                CREATE TABLE ton_connection (
                    metaId INTEGER NOT NULL PRIMARY KEY,
                    attackerPayload BLOB
                )
                """.trimIndent()
            )
        }

        assertRejectedBeforePreferenceMutation(
            databaseName = INCOMPATIBLE_TON_CONNECTION_DATABASE,
            expectedMessage =
            "missing, extra, or incompatible SQLite schema objects"
        )
    }

    @Test
    fun nonTableDownstreamDropTargetFailsBeforePreferenceMutation() {
        installFixture(NON_TABLE_DROP_TARGET_DATABASE)
        mutateRawDatabase(NON_TABLE_DROP_TARGET_DATABASE) {
            execSQL("DROP TABLE users")
            execSQL(
                "CREATE VIEW users AS " +
                    "SELECT 'malformed' AS address"
            )
        }

        assertRejectedBeforePreferenceMutation(
            databaseName = NON_TABLE_DROP_TARGET_DATABASE,
            expectedMessage =
            "missing, extra, or incompatible SQLite schema objects"
        )
    }

    @Test
    fun danglingDeferredForeignKeyFailsBeforePreferenceMutation() {
        installFixture(DANGLING_FOREIGN_KEY_DATABASE)
        mutateRawDatabase(DANGLING_FOREIGN_KEY_DATABASE) {
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
                    999L,
                    "missing-chain",
                    ByteArray(32) { 0x11 },
                    ByteArray(32) { 0x22 },
                    "SR25519",
                    "dangling",
                    1
                )
            )
        }

        assertRejectedBeforePreferenceMutation(
            databaseName = DANGLING_FOREIGN_KEY_DATABASE,
            expectedMessage = "foreign-key violation"
        )
    }

    @Test
    fun exactForeignKeyAndRebuildInputLimitsPassWithoutPreferenceMutation() {
        installFixture(EXACT_FOREIGN_KEY_LIMIT_DATABASE)
        insertPoolRows(EXACT_FOREIGN_KEY_LIMIT_DATABASE, userPoolRows = 1)
        insertTokenPriceRows(
            EXACT_FOREIGN_KEY_LIMIT_DATABASE,
            tokenPriceRows = 1
        )
        val preferenceProbe = EncryptedPreferenceMutationProbe()
        val exactBefore = preferenceProbe.rawSnapshot()
        val limits = foreignKeyCheckLimits(
            "userpools" to 1,
            "token_price" to 1
        )

        withLogicalVersion71Schema(
            EXACT_FOREIGN_KEY_LIMIT_DATABASE
        ) { database ->
            TonUpgradeSqlPreflight.requireSafeToMutate(
                database = database,
                foreignKeyCheckLimits = limits
            )
        }

        assertRawSnapshotsEqual(exactBefore, preferenceProbe.rawSnapshot())
    }

    @Test
    fun foreignKeyChildLimitPlusOneFailsBeforePreferenceMutation() {
        installFixture(FOREIGN_KEY_LIMIT_PLUS_ONE_DATABASE)
        insertPoolRows(
            FOREIGN_KEY_LIMIT_PLUS_ONE_DATABASE,
            userPoolRows = 2
        )

        assertRejectedBeforePreferenceMutation(
            databaseName = FOREIGN_KEY_LIMIT_PLUS_ONE_DATABASE,
            expectedMessage =
            "userpools rows exceed the safe migration limit",
            foreignKeyCheckLimits = foreignKeyCheckLimits(
                "userpools" to 1
            )
        )
    }

    @Test
    fun nonForeignRebuildInputLimitPlusOneFailsBeforePreferenceMutation() {
        installFixture(REBUILD_INPUT_LIMIT_PLUS_ONE_DATABASE)
        insertTokenPriceRows(
            REBUILD_INPUT_LIMIT_PLUS_ONE_DATABASE,
            tokenPriceRows = 2
        )

        assertRejectedBeforePreferenceMutation(
            databaseName = REBUILD_INPUT_LIMIT_PLUS_ONE_DATABASE,
            expectedMessage =
            "token_price rows exceed the safe migration limit",
            foreignKeyCheckLimits = foreignKeyCheckLimits(
                "token_price" to 1
            )
        )
    }

    @Test
    fun oversizedCopiedTextFailsBeforePreferenceMutation() {
        installFixture(OVERSIZED_COPIED_TEXT_DATABASE)
        mutateRawDatabase(OVERSIZED_COPIED_TEXT_DATABASE) {
            execSQL(
                """
                INSERT INTO token_price(
                    priceId,
                    fiatRate,
                    fiatSymbol,
                    recentRateChange
                ) VALUES('oversized-rate', ?, 'USD', '0')
                """.trimIndent(),
                arrayOf(
                    "9".repeat(
                        TON_UPGRADE_MAX_CELL_PAYLOAD_BYTES.toInt() + 1
                    )
                )
            )
        }

        assertRejectedBeforePreferenceMutation(
            databaseName = OVERSIZED_COPIED_TEXT_DATABASE,
            expectedMessage =
            "token_price.fiatRate exceeds the safe migration payload limit"
        )
    }

    @Test
    fun exactCopiedTextPayloadLimitPassesWithoutPreferenceMutation() {
        installFixture(EXACT_COPIED_TEXT_DATABASE)
        mutateRawDatabase(EXACT_COPIED_TEXT_DATABASE) {
            execSQL(
                """
                INSERT INTO token_price(
                    priceId,
                    fiatRate,
                    fiatSymbol,
                    recentRateChange
                ) VALUES('exact-rate', ?, 'USD', '0')
                """.trimIndent(),
                arrayOf(
                    "9".repeat(
                        TON_UPGRADE_MAX_CELL_PAYLOAD_BYTES.toInt()
                    )
                )
            )
        }
        val preferenceProbe = EncryptedPreferenceMutationProbe()
        val exactBefore = preferenceProbe.rawSnapshot()

        withLogicalVersion71Schema(
            EXACT_COPIED_TEXT_DATABASE
        ) { database ->
            TonUpgradeSqlPreflight.requireSafeToMutate(database)
        }

        assertRawSnapshotsEqual(exactBefore, preferenceProbe.rawSnapshot())
    }

    @Test
    fun exactCumulativeCopiedPayloadBudgetPassesWithoutPreferenceMutation() {
        installFixture(EXACT_CUMULATIVE_PAYLOAD_DATABASE)
        insertBudgetTokenPriceRow(EXACT_CUMULATIVE_PAYLOAD_DATABASE)
        val preferenceProbe = EncryptedPreferenceMutationProbe()
        val exactBefore = preferenceProbe.rawSnapshot()

        withLogicalVersion71Schema(
            EXACT_CUMULATIVE_PAYLOAD_DATABASE
        ) { database ->
            TonUpgradeSqlPreflight.requireSafeToMutate(
                database = database,
                payloadLimits = budgetPayloadLimits(
                    maximumTotalBytes = BUDGET_TOKEN_PRICE_PAYLOAD_BYTES
                )
            )
        }

        assertRawSnapshotsEqual(exactBefore, preferenceProbe.rawSnapshot())
    }

    @Test
    fun cumulativeCopiedPayloadBudgetPlusOneFailsBeforePreferenceMutation() {
        installFixture(CUMULATIVE_PAYLOAD_PLUS_ONE_DATABASE)
        insertBudgetTokenPriceRow(CUMULATIVE_PAYLOAD_PLUS_ONE_DATABASE)

        assertRejectedBeforePreferenceMutation(
            databaseName = CUMULATIVE_PAYLOAD_PLUS_ONE_DATABASE,
            expectedMessage =
            "copied tables exceed the safe cumulative migration payload limit",
            payloadLimits = budgetPayloadLimits(
                maximumTotalBytes = BUDGET_TOKEN_PRICE_PAYLOAD_BYTES - 1L
            )
        )
    }

    @Test
    fun firstOversizedCopiedRowFailsBeforeLaterOversizedColumn() {
        installFixture(FIRST_OVERSIZED_PAYLOAD_DATABASE)
        mutateRawDatabase(FIRST_OVERSIZED_PAYLOAD_DATABASE) {
            execSQL("DELETE FROM token_price")
            execSQL(
                """
                INSERT INTO token_price(
                    rowid,
                    priceId,
                    fiatRate,
                    fiatSymbol,
                    recentRateChange
                ) VALUES(1, 'a', '1', 'USD', '123456789')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO token_price(
                    rowid,
                    priceId,
                    fiatRate,
                    fiatSymbol,
                    recentRateChange
                ) VALUES(2, 'b', '123456789', 'USD', '0')
                """.trimIndent()
            )
        }

        assertRejectedBeforePreferenceMutation(
            databaseName = FIRST_OVERSIZED_PAYLOAD_DATABASE,
            expectedMessage =
            "token_price.recentRateChange exceeds the safe migration " +
                "payload limit",
            payloadLimits = TonUpgradePayloadLimits(
                maximumCellBytes = 8L,
                maximumRowBytes = 64L,
                maximumTotalBytes = 128L
            )
        )
    }

    @Test
    fun payloadByteAccountingAcceptsExactLongBoundaryAndRejectsOverflow() {
        assertEquals(
            Long.MAX_VALUE,
            checkedTonUpgradePayloadByteSum(
                currentBytes = Long.MAX_VALUE - 1L,
                additionalBytes = 1L,
                context = "test payload"
            )
        )

        val failure = runCatching {
            checkedTonUpgradePayloadByteSum(
                currentBytes = Long.MAX_VALUE,
                additionalBytes = 1L,
                context = "test payload"
            )
        }.exceptionOrNull()

        assertTrue(
            "Expected TonUpgradeSqlIntegrityException, found $failure",
            failure is TonUpgradeSqlIntegrityException
        )
        assertTrue(
            failure?.message?.contains("payload accounting range") == true
        )
    }

    @Test
    fun productionForeignKeySchemaCapsMatchReleasedSchemas() {
        assertEquals(
            128,
            TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.maximumSchemaObjects
        )
        assertEquals(
            48,
            TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.maximumOrdinaryTables
        )
        assertEquals(
            2,
            TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS
                .maximumForeignKeyDefinitionsPerTable
        )
    }

    @Test
    fun oversizedSqliteMasterTypeFailsWithoutMaterializingOrMutatingPreferences() {
        installFixture(OVERSIZED_SCHEMA_TYPE_DATABASE)

        assertOpenDatabaseCorruptionRejectedBeforePreferenceMutation(
            databaseName = OVERSIZED_SCHEMA_TYPE_DATABASE,
            expectedMessage =
            "unsafe schema object type"
        ) { database ->
            replaceOpenSchemaMetadata(
                database = database,
                column = "type",
                value = "x".repeat(OVERSIZED_SCHEMA_METADATA_BYTES)
            )
        }
    }

    @Test
    fun oversizedSqliteMasterSqlFailsWithoutMaterializingOrMutatingPreferences() {
        installFixture(OVERSIZED_SCHEMA_SQL_DATABASE)

        assertOpenDatabaseCorruptionRejectedBeforePreferenceMutation(
            databaseName = OVERSIZED_SCHEMA_SQL_DATABASE,
            expectedMessage =
            "chains sqlite_master SQL is not a safe bounded SQLite string"
        ) { database ->
            replaceOpenSchemaMetadata(
                database = database,
                column = "sql",
                value = "x".repeat(OVERSIZED_SCHEMA_METADATA_BYTES)
            )
        }
    }

    private fun assertRejectedBeforePreferenceMutation(
        databaseName: String,
        expectedMessage: String,
        foreignKeyCheckLimits: MigrationForeignKeyCheckLimits =
            TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS,
        payloadLimits: TonUpgradePayloadLimits =
            TonUpgradePayloadLimits.PRODUCTION
    ) {
        val preferenceProbe = EncryptedPreferenceMutationProbe()
        val exactBefore = preferenceProbe.rawSnapshot()
        val failure = withLogicalVersion71Schema(databaseName) { database ->
            runCatching {
                TonUpgradeSqlPreflight.requireSafeToMutate(
                    database = database,
                    foreignKeyCheckLimits = foreignKeyCheckLimits,
                    payloadLimits = payloadLimits
                )
                preferenceProbe.simulateEncryptedPreferenceCommit()
            }.exceptionOrNull()
        }

        assertRawSnapshotsEqual(exactBefore, preferenceProbe.rawSnapshot())
        assertTrue(
            "Expected TonUpgradeSqlIntegrityException, found $failure",
            failure is TonUpgradeSqlIntegrityException
        )
        assertTrue(
            "Expected failure containing '$expectedMessage', found " +
                failure?.message,
            failure?.message?.contains(expectedMessage) == true
        )
    }

    private fun insertPoolRows(
        databaseName: String,
        userPoolRows: Int
    ) {
        mutateRawDatabase(databaseName) {
            execSQL("DELETE FROM userpools")
            execSQL("DELETE FROM allpools")
            execSQL(
                """
                INSERT INTO allpools(
                    tokenIdBase,
                    tokenIdTarget,
                    reserveBase,
                    reserveTarget,
                    totalIssuance,
                    reservesAccount
                ) VALUES('base', 'target', '1', '2', '3', 'reserves')
                """.trimIndent()
            )
            repeat(userPoolRows) { index ->
                execSQL(
                    """
                    INSERT INTO userpools(
                        userTokenIdBase,
                        userTokenIdTarget,
                        accountAddress,
                        poolProvidersBalance
                    ) VALUES('base', 'target', ?, '1')
                    """.trimIndent(),
                    arrayOf("bounded-pool-account-$index")
                )
            }
        }
    }

    private fun insertTokenPriceRows(
        databaseName: String,
        tokenPriceRows: Int
    ) {
        mutateRawDatabase(databaseName) {
            execSQL("DELETE FROM token_price")
            repeat(tokenPriceRows) { index ->
                execSQL(
                    """
                    INSERT INTO token_price(
                        priceId,
                        fiatRate,
                        fiatSymbol,
                        recentRateChange
                    ) VALUES(?, '1', 'USD', '0')
                    """.trimIndent(),
                    arrayOf("bounded-price-$index")
                )
            }
        }
    }

    private fun insertBudgetTokenPriceRow(databaseName: String) {
        mutateRawDatabase(databaseName) {
            execSQL("DELETE FROM token_price")
            execSQL(
                """
                INSERT INTO token_price(
                    priceId,
                    fiatRate,
                    fiatSymbol,
                    recentRateChange
                ) VALUES('budget', '1', 'USD', '0')
                """.trimIndent()
            )
        }
    }

    private fun budgetPayloadLimits(
        maximumTotalBytes: Long
    ): TonUpgradePayloadLimits {
        return TonUpgradePayloadLimits(
            maximumCellBytes = 64L,
            maximumRowBytes = 64L,
            maximumTotalBytes = maximumTotalBytes
        )
    }

    private fun foreignKeyCheckLimits(
        vararg rowLimits: Pair<String, Int>
    ): MigrationForeignKeyCheckLimits {
        val rowsByTable = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS
            .maximumRowsByTable
            .toMutableMap()
        rowLimits.forEach { (tableName, maximumRows) ->
            rowsByTable[tableName] = maximumRows
        }
        return TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.copy(
            maximumRowsByTable = rowsByTable
        )
    }

    private fun assertOpenDatabaseCorruptionRejectedBeforePreferenceMutation(
        databaseName: String,
        expectedMessage: String,
        corrupt: (SupportSQLiteDatabase) -> Unit
    ) {
        val preferenceProbe = EncryptedPreferenceMutationProbe()
        val exactBefore = preferenceProbe.rawSnapshot()
        val failure = withLogicalVersion71Schema(databaseName) { database ->
            corrupt(database)
            runCatching {
                TonUpgradeSqlPreflight.requireSafeToMutate(database)
                preferenceProbe.simulateEncryptedPreferenceCommit()
            }.exceptionOrNull()
        }

        assertRawSnapshotsEqual(exactBefore, preferenceProbe.rawSnapshot())
        assertTrue(
            "Expected TonUpgradeSqlIntegrityException, found $failure",
            failure is TonUpgradeSqlIntegrityException
        )
        assertTrue(
            "Expected failure containing '$expectedMessage', found " +
                failure?.message,
            failure?.message?.contains(expectedMessage) == true
        )
    }

    private fun replaceOpenSchemaMetadata(
        database: SupportSQLiteDatabase,
        column: String,
        value: String
    ) {
        check(column == "type" || column == "sql")
        database.execSQL("PRAGMA writable_schema = ON")
        try {
            database.execSQL(
                "UPDATE sqlite_master SET `$column` = ? " +
                    "WHERE name = 'chains' COLLATE BINARY",
                arrayOf(value)
            )
        } finally {
            database.execSQL("PRAGMA writable_schema = OFF")
        }
        database.query(
            "SELECT length(CAST(`$column` AS BLOB)) AS byteLength " +
                "FROM sqlite_master " +
                "WHERE name = 'chains' COLLATE BINARY LIMIT 2"
        ).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(
                OVERSIZED_SCHEMA_METADATA_BYTES.toLong(),
                cursor.getLong(cursor.getColumnIndexOrThrow("byteLength"))
            )
            check(!cursor.moveToNext())
        }
    }

    private fun installFixture(databaseName: String) {
        ReleasedV374DatabaseFixture.install(context, databaseName)
    }

    private fun <T> withLogicalVersion71Schema(
        databaseName: String,
        openVersion: Int = ReleasedV374DatabaseFixture.DATABASE_VERSION,
        action: (SupportSQLiteDatabase) -> T
    ): T {
        val callback = object :
            SupportSQLiteOpenHelper.Callback(openVersion) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("The frozen version 71 fixture was not installed")
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                error(
                    "Unexpected fixture migration from $oldVersion to $newVersion"
                )
            }
        }
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
        return try {
            val database = openHelper.writableDatabase
            database.setForeignKeyConstraintsEnabled(true)
            action(database)
        } finally {
            openHelper.close()
        }
    }

    private fun mutateRawDatabase(
        databaseName: String,
        mutation: SQLiteDatabase.() -> Unit
    ) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use { database ->
            database.setForeignKeyConstraintsEnabled(false)
            database.mutation()
        }
    }

    private fun replaceTableSchemaSql(
        databaseName: String,
        tableName: String,
        oldDefinition: String,
        newDefinition: String
    ) {
        mutateRawDatabase(databaseName) {
            val original = singleString(
                "SELECT sql FROM sqlite_master " +
                    "WHERE type = 'table' AND name = ?",
                tableName
            )
            assertEquals(
                "Expected one exact schema fragment",
                1,
                original.windowed(oldDefinition.length)
                    .count { it == oldDefinition }
            )
            val replacement = original.replace(
                oldDefinition,
                newDefinition
            )
            execSQL("PRAGMA writable_schema = ON")
            try {
                execSQL(
                    "UPDATE sqlite_master SET sql = ? " +
                        "WHERE type = 'table' AND name = ? AND sql = ?",
                    arrayOf(replacement, tableName, original)
                )
                assertEquals(
                    replacement,
                    singleString(
                        "SELECT sql FROM sqlite_master " +
                            "WHERE type = 'table' AND name = ?",
                        tableName
                    )
                )
            } finally {
                execSQL("PRAGMA writable_schema = OFF")
            }
            val nextSchemaVersion =
                singleInt("PRAGMA schema_version") + 1
            execSQL("PRAGMA schema_version = $nextSchemaVersion")
        }
    }

    private fun SQLiteDatabase.singleString(
        sql: String,
        vararg bindArgs: String
    ): String {
        return rawQuery(sql, bindArgs).use { cursor ->
            check(cursor.moveToFirst())
            val value = cursor.getString(0)
            check(!cursor.moveToNext())
            value
        }
    }

    private fun SQLiteDatabase.singleInt(sql: String): Int {
        return rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst())
            val value = cursor.getInt(0)
            check(!cursor.moveToNext())
            value
        }
    }

    private fun assertRawSnapshotsEqual(
        expected: Map<String, ByteArray>,
        actual: Map<String, ByteArray>
    ) {
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (key, expectedValue) ->
            val actualValue = actual[key]
            assertNotNull(actualValue)
            assertArrayEquals(expectedValue, checkNotNull(actualValue))
        }
    }

    private class EncryptedPreferenceMutationProbe {
        private val rawCiphertexts = linkedMapOf(
            "7:ACCESS_SECRETS" to
                byteArrayOf(0x66, 0x65, 0x61, 0x72),
            "wallet_public_identity_recovery:7:synthetic" to
                byteArrayOf(0x10, 0x20, 0x30, 0x40)
        )

        fun rawSnapshot(): Map<String, ByteArray> {
            return rawCiphertexts.mapValues { (_, value) -> value.clone() }
        }

        fun simulateEncryptedPreferenceCommit() {
            rawCiphertexts["7:ACCESS_SECRETS"] =
                byteArrayOf(0x00, 0x01, 0x02)
            rawCiphertexts["7:SUBSTRATE_SECRETS"] =
                byteArrayOf(0x03, 0x04, 0x05)
        }
    }

    private data class FutureColumnCase(
        val databaseName: String,
        val tableName: String,
        val columnName: String
    )

    private companion object {
        const val CANONICAL_DATABASE = "ton-sql-preflight-canonical"
        const val OLDER_TRANSACTION_METADATA_DATABASE =
            "ton-sql-preflight-older-transaction-metadata"
        const val COMPATIBLE_TON_CONNECTION_DATABASE =
            "ton-sql-preflight-compatible-ton"
        const val MALFORMED_COLUMN_DATABASE =
            "ton-sql-preflight-malformed-column"
        const val MISSING_ADDRESS_BOOK_INDEX_DATABASE =
            "ton-sql-preflight-missing-address-index"
        const val ALTERED_ADDRESS_BOOK_INDEX_DATABASE =
            "ton-sql-preflight-altered-address-index"
        const val DESCENDING_ADDRESS_BOOK_INDEX_DATABASE =
            "ton-sql-preflight-descending-address-index"
        const val ALTERED_RETAINED_DEFAULT_DATABASE =
            "ton-sql-preflight-altered-retained-default"
        const val RETAINED_CHECK_CLAUSE_DATABASE =
            "ton-sql-preflight-retained-check"
        const val ADDITIONAL_TRIGGER_DATABASE =
            "ton-sql-preflight-additional-trigger"
        const val RELEASED_DEFAULT_VARIANTS_DATABASE =
            "ton-sql-preflight-released-defaults"
        const val UNSHIPPED_META_DEFAULT_DATABASE =
            "ton-sql-preflight-unshipped-meta-default"
        const val UNSHIPPED_CHAIN_DEFAULT_DATABASE =
            "ton-sql-preflight-unshipped-chain-default"
        const val MISSING_COLUMN_DATABASE =
            "ton-sql-preflight-missing-column"
        const val INCOMPATIBLE_TON_CONNECTION_DATABASE =
            "ton-sql-preflight-incompatible-ton"
        const val NON_TABLE_DROP_TARGET_DATABASE =
            "ton-sql-preflight-non-table-drop"
        const val DANGLING_FOREIGN_KEY_DATABASE =
            "ton-sql-preflight-dangling-foreign-key"
        const val EXACT_FOREIGN_KEY_LIMIT_DATABASE =
            "ton-sql-preflight-exact-foreign-key-limit"
        const val FOREIGN_KEY_LIMIT_PLUS_ONE_DATABASE =
            "ton-sql-preflight-foreign-key-limit-plus-one"
        const val REBUILD_INPUT_LIMIT_PLUS_ONE_DATABASE =
            "ton-sql-preflight-rebuild-input-limit-plus-one"
        const val OVERSIZED_COPIED_TEXT_DATABASE =
            "ton-sql-preflight-oversized-copied-text"
        const val EXACT_COPIED_TEXT_DATABASE =
            "ton-sql-preflight-exact-copied-text"
        const val EXACT_CUMULATIVE_PAYLOAD_DATABASE =
            "ton-sql-preflight-exact-cumulative-payload"
        const val CUMULATIVE_PAYLOAD_PLUS_ONE_DATABASE =
            "ton-sql-preflight-cumulative-payload-plus-one"
        const val FIRST_OVERSIZED_PAYLOAD_DATABASE =
            "ton-sql-preflight-first-oversized-payload"
        const val OVERSIZED_SCHEMA_TYPE_DATABASE =
            "ton-sql-preflight-oversized-schema-type"
        const val OVERSIZED_SCHEMA_SQL_DATABASE =
            "ton-sql-preflight-oversized-schema-sql"
        const val OVERSIZED_SCHEMA_METADATA_BYTES = 4 * 1024 * 1024
        const val BUDGET_TOKEN_PRICE_PAYLOAD_BYTES = 11L

        val FUTURE_COLUMN_CASES = listOf(
            FutureColumnCase(
                databaseName = "ton-sql-preflight-existing-ton-public-key",
                tableName = "meta_accounts",
                columnName = "tonPublicKey"
            ),
            FutureColumnCase(
                databaseName = "ton-sql-preflight-existing-ecosystem",
                tableName = "chains",
                columnName = "ecosystem"
            ),
            FutureColumnCase(
                databaseName = "ton-sql-preflight-existing-min-version",
                tableName = "chains",
                columnName = "androidMinAppVersion"
            ),
            FutureColumnCase(
                databaseName = "ton-sql-preflight-existing-ton-bridge",
                tableName = "chains",
                columnName = "tonBridgeUrl"
            ),
            FutureColumnCase(
                databaseName = "ton-sql-preflight-existing-xcm",
                tableName = "chains",
                columnName = "xcm"
            ),
            FutureColumnCase(
                databaseName = "ton-sql-preflight-existing-coinbase",
                tableName = "chain_assets",
                columnName = "coinbaseUrl"
            )
        )

        val DATABASE_NAMES = buildSet {
            add(CANONICAL_DATABASE)
            add(OLDER_TRANSACTION_METADATA_DATABASE)
            add(COMPATIBLE_TON_CONNECTION_DATABASE)
            add(MALFORMED_COLUMN_DATABASE)
            add(MISSING_ADDRESS_BOOK_INDEX_DATABASE)
            add(ALTERED_ADDRESS_BOOK_INDEX_DATABASE)
            add(DESCENDING_ADDRESS_BOOK_INDEX_DATABASE)
            add(ALTERED_RETAINED_DEFAULT_DATABASE)
            add(RETAINED_CHECK_CLAUSE_DATABASE)
            add(ADDITIONAL_TRIGGER_DATABASE)
            add(RELEASED_DEFAULT_VARIANTS_DATABASE)
            add(UNSHIPPED_META_DEFAULT_DATABASE)
            add(UNSHIPPED_CHAIN_DEFAULT_DATABASE)
            add(MISSING_COLUMN_DATABASE)
            add(INCOMPATIBLE_TON_CONNECTION_DATABASE)
            add(NON_TABLE_DROP_TARGET_DATABASE)
            add(DANGLING_FOREIGN_KEY_DATABASE)
            add(EXACT_FOREIGN_KEY_LIMIT_DATABASE)
            add(FOREIGN_KEY_LIMIT_PLUS_ONE_DATABASE)
            add(REBUILD_INPUT_LIMIT_PLUS_ONE_DATABASE)
            add(OVERSIZED_COPIED_TEXT_DATABASE)
            add(EXACT_COPIED_TEXT_DATABASE)
            add(EXACT_CUMULATIVE_PAYLOAD_DATABASE)
            add(CUMULATIVE_PAYLOAD_PLUS_ONE_DATABASE)
            add(FIRST_OVERSIZED_PAYLOAD_DATABASE)
            add(OVERSIZED_SCHEMA_TYPE_DATABASE)
            add(OVERSIZED_SCHEMA_SQL_DATABASE)
            FUTURE_COLUMN_CASES.forEach { add(it.databaseName) }
        }

        val COMPATIBLE_TON_CONNECTION_SQL =
            """
            CREATE TABLE IF NOT EXISTS `ton_connection` (
                `metaId` INTEGER NOT NULL,
                `clientId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `icon` TEXT NOT NULL,
                `url` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                PRIMARY KEY(`metaId`, `url`, `source`),
                FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
    }
}
