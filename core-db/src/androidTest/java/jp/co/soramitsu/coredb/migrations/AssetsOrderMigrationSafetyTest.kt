package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV200DatabaseFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LargeClass", "LongParameterList", "MagicNumber")
class AssetsOrderMigrationSafetyTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val createdDatabases = mutableSetOf<String>()

    @After
    fun deleteTestDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
        createdDatabases.clear()
    }

    @Test
    fun frozenReleasedSchemaAtEveryExactBoundMigratesAndSorts() {
        withReleasedVersion34Database(EXACT_LIMIT_DATABASE) { database ->
            insertWalletAndChain(database)
            insertAsset(
                database = database,
                symbol = FUNDED_SYMBOL,
                free = "250",
                reserved = "50",
                dollarRate = "2.5",
                precision = 2
            )
            insertAsset(
                database = database,
                symbol = EMPTY_SYMBOL,
                free = "0",
                reserved = "0",
                dollarRate = null,
                precision = 12
            )

            AssetsOrderMigration(
                AssetsOrderMigrationLimits(
                    maxWalletRows = 1,
                    maxAssetRows = 2,
                    maxTokenRows = 2,
                    maxChainAssetRows = 2,
                    maxChainRows = 1,
                    maxModelsPerWallet = 2
                )
            ).migrate(database)

            assertTrue(database.hasColumn("assets", "sortIndex"))
            assertTrue(database.hasColumn("assets", "enabled"))
            assertFalse(database.hasTemporaryNormalizationLookup())
            assertEquals(
                0,
                database.singleInt(
                    "SELECT sortIndex FROM assets WHERE tokenSymbol = ?",
                    FUNDED_SYMBOL
                )
            )
            assertEquals(
                1,
                database.singleInt(
                    "SELECT sortIndex FROM assets WHERE tokenSymbol = ?",
                    EMPTY_SYMBOL
                )
            )
        }
    }

    @Test
    fun assetLimitPlusOneFailsBeforeEitherColumnIsAdded() {
        withReleasedVersion34Database(
            ASSET_LIMIT_PLUS_ONE_DATABASE
        ) { database ->
            insertWalletAndChain(database)
            insertAsset(database, FUNDED_SYMBOL)
            insertAsset(database, EMPTY_SYMBOL)

            assertThrows(
                AssetsOrderMigrationIntegrityException::class.java
            ) {
                AssetsOrderMigration(
                    AssetsOrderMigrationLimits(
                        maxWalletRows = 1,
                        maxAssetRows = 1,
                        maxTokenRows = 2,
                        maxChainAssetRows = 2,
                        maxChainRows = 1,
                        maxModelsPerWallet = 1
                    )
                ).migrate(database)
            }

            assertFalse(database.hasColumn("assets", "sortIndex"))
            assertFalse(database.hasColumn("assets", "enabled"))
        }
    }

    @Test
    fun perWalletModelLimitPlusOneFailsBeforeEitherColumnIsAdded() {
        withReleasedVersion34Database(
            MODEL_LIMIT_PLUS_ONE_DATABASE
        ) { database ->
            insertWalletAndChain(database)
            insertAsset(database, FUNDED_SYMBOL)
            insertAsset(database, EMPTY_SYMBOL)

            assertPreflightFailureBeforeSchemaMutation(
                database = database,
                limits = AssetsOrderMigrationLimits(
                    maxWalletRows = 1,
                    maxAssetRows = 2,
                    maxTokenRows = 2,
                    maxChainAssetRows = 2,
                    maxChainRows = 1,
                    maxModelsPerWallet = 1
                )
            )
        }
    }

    @Test
    fun unrelatedAssetsOnOneChainCannotAmplifyTheJoin() {
        withReleasedVersion34Database(
            JOIN_AMPLIFICATION_DATABASE
        ) { database ->
            insertWalletAndChain(database)
            insertAsset(database, FUNDED_SYMBOL)
            repeat(ADVERSARIAL_CHAIN_ASSET_ROWS) { index ->
                insertChainAsset(
                    database = database,
                    symbol = "unrelated-$index",
                    precision = index % 18
                )
            }

            AssetsOrderMigration(
                AssetsOrderMigrationLimits(
                    maxWalletRows = 1,
                    maxAssetRows = 1,
                    maxTokenRows = 1,
                    maxChainAssetRows =
                    ADVERSARIAL_CHAIN_ASSET_ROWS + 1,
                    maxChainRows = 1,
                    maxModelsPerWallet = 1
                )
            ).migrate(database)

            assertEquals(
                0,
                database.singleInt(
                    "SELECT sortIndex FROM assets WHERE tokenSymbol = ?",
                    FUNDED_SYMBOL
                )
            )
        }
    }

    @Test
    fun lowercaseReleasedIdMapsToUppercaseHistoricalSymbol() {
        withReleasedVersion34Database(
            CASE_NORMALIZATION_DATABASE
        ) { database ->
            insertWalletAndChain(database)
            insertAsset(
                database = database,
                symbol = "DOT",
                chainAssetId = "dOt"
            )

            AssetsOrderMigration(exactSingleRowLimits()).migrate(database)

            assertEquals(
                0,
                database.singleInt(
                    "SELECT sortIndex FROM assets WHERE tokenSymbol = 'DOT'"
                )
            )
        }
    }

    @Test
    fun normalizedChainAssetCollisionFailsBeforeSchemaMutation() {
        withReleasedVersion34Database(
            NORMALIZATION_COLLISION_DATABASE
        ) { database ->
            insertWalletAndChain(database)
            insertAsset(
                database = database,
                symbol = "DOT",
                chainAssetId = "dot"
            )
            insertChainAsset(database, "DoT", precision = 12)

            assertPreflightFailureBeforeSchemaMutation(
                database = database,
                limits = exactSingleRowLimits().copy(
                    maxChainAssetRows = 2
                )
            )
        }
    }

    @Test
    fun multipleWalletsAreSortedWithinThePerWalletMemoryBound() {
        withReleasedVersion34Database(
            MULTI_WALLET_DATABASE
        ) { database ->
            insertWalletAndChain(database)
            insertWallet(database, SECOND_META_ID, "Second")
            insertAsset(database, FUNDED_SYMBOL)
            insertAsset(
                database = database,
                symbol = FUNDED_SYMBOL,
                metaId = SECOND_META_ID,
                accountId = SECOND_ACCOUNT_ID,
                registerTokenAndChainAsset = false
            )

            AssetsOrderMigration(
                AssetsOrderMigrationLimits(
                    maxWalletRows = 2,
                    maxAssetRows = 2,
                    maxTokenRows = 1,
                    maxChainAssetRows = 1,
                    maxChainRows = 1,
                    maxModelsPerWallet = 1
                )
            ).migrate(database)

            assertEquals(
                0,
                database.singleInt(
                    "SELECT sortIndex FROM assets WHERE metaId = ?",
                    META_ID
                )
            )
            assertEquals(
                0,
                database.singleInt(
                    "SELECT sortIndex FROM assets WHERE metaId = ?",
                    SECOND_META_ID
                )
            )
        }
    }

    @Test
    fun oversizedBalanceFailsBeforeSchemaMutationOrBigIntegerParsing() {
        withReleasedVersion34Database(
            OVERSIZED_BALANCE_DATABASE
        ) { database ->
            insertWalletAndChain(database)
            insertAsset(database, FUNDED_SYMBOL)
            database.execSQL(
                "UPDATE assets SET freeInPlanks = ?",
                arrayOf("9".repeat(MAX_BALANCE_BYTES + 1))
            )

            assertPreflightFailureBeforeSchemaMutation(database)
        }
    }

    @Test
    fun oversizedChainNameFailsBeforeSchemaMutationOrMaterialization() {
        withReleasedVersion34Database(
            OVERSIZED_CHAIN_NAME_DATABASE
        ) { database ->
            insertWalletAndChain(database)
            insertAsset(database, FUNDED_SYMBOL)
            database.execSQL(
                "UPDATE chains SET name = ?",
                arrayOf("n".repeat(MAX_CHAIN_NAME_BYTES + 1))
            )

            assertPreflightFailureBeforeSchemaMutation(database)
        }
    }

    @Test
    fun invalidPrecisionAndTestnetRangesAreRejected() {
        listOf(
            INVALID_PRECISION_DATABASE to
                "UPDATE chain_assets SET precision = 256",
            INVALID_TESTNET_DATABASE to
                "UPDATE chains SET isTestNet = 2"
        ).forEach { (databaseName, invalidMutation) ->
            withReleasedVersion34Database(databaseName) { database ->
                insertWalletAndChain(database)
                insertAsset(database, FUNDED_SYMBOL)
                database.execSQL(invalidMutation)

                assertPreflightFailureBeforeSchemaMutation(database)
            }
        }
    }

    @Test
    fun releasedTokenAndChainPrimaryKeysAreRequiredBeforeSchemaMutation() {
        val mutations:
            List<Pair<String, (SupportSQLiteDatabase) -> Unit>> = listOf(
                MISSING_TOKEN_PRIMARY_KEY_DATABASE to
                    ::replaceTokensWithoutPrimaryKey,
                MISSING_CHAIN_PRIMARY_KEY_DATABASE to
                    ::replaceChainsWithoutPrimaryKey
            )

        mutations.forEach { (databaseName, removePrimaryKey) ->
            withReleasedVersion34Database(databaseName) { database ->
                insertWalletAndChain(database)
                insertAsset(database, FUNDED_SYMBOL)
                removePrimaryKey(database)

                assertPreflightFailureBeforeSchemaMutation(database)
            }
        }
    }

    @Test
    fun missingReleasedAssetWalletIndexFailsBeforeAnyMigrationMutation() {
        withReleasedVersion34Database(
            MISSING_ASSET_META_ID_INDEX_DATABASE
        ) { database ->
            insertWalletAndChain(database)
            insertAsset(database, FUNDED_SYMBOL)
            database.execSQL("DROP INDEX index_assets_metaId")

            assertPreflightFailureBeforeSchemaMutation(database)
            assertEquals(
                1,
                database.singleInt("SELECT COUNT(*) FROM assets")
            )
        }
    }

    @Test
    fun unusedRegistryPayloadsAreNotMaterializedOrParsed() {
        withReleasedVersion34Database(
            UNUSED_REGISTRY_PAYLOAD_DATABASE
        ) { database ->
            insertWalletAndChain(database)
            insertAsset(database, FUNDED_SYMBOL)
            insertUnusedTokenWithOversizedPayload(database)
            insertUnusedChainWithOversizedPayload(database)

            AssetsOrderMigration(
                exactSingleRowLimits().copy(
                    maxTokenRows = 2,
                    maxChainRows = 2
                )
            ).migrate(database)

            assertEquals(
                UNUSED_REGISTRY_PAYLOAD_BYTES,
                database.singleInt(
                    "SELECT length(dollarRate) FROM tokens WHERE symbol = ?",
                    UNUSED_TOKEN_SYMBOL
                )
            )
            assertEquals(
                UNUSED_REGISTRY_PAYLOAD_BYTES,
                database.singleInt(
                    "SELECT length(name) FROM chains WHERE id = ?",
                    UNUSED_CHAIN_ID
                )
            )
            assertEquals(
                0,
                database.singleInt(
                    "SELECT sortIndex FROM assets WHERE tokenSymbol = ?",
                    FUNDED_SYMBOL
                )
            )
        }
    }

    @Test
    fun unusedRegistryRowsStillEnforceEachGlobalRowCap() {
        val additions:
            List<Pair<String, (SupportSQLiteDatabase) -> Unit>> = listOf(
                UNUSED_TOKEN_LIMIT_DATABASE to
                    ::insertUnusedTokenWithOversizedPayload,
                UNUSED_CHAIN_LIMIT_DATABASE to
                    ::insertUnusedChainWithOversizedPayload
            )

        additions.forEach { (databaseName, insertUnusedRow) ->
            withReleasedVersion34Database(databaseName) { database ->
                insertWalletAndChain(database)
                insertAsset(database, FUNDED_SYMBOL)
                insertUnusedRow(database)

                assertPreflightFailureBeforeSchemaMutation(database)
            }
        }
    }

    @Test
    fun triggerSuppressedRowIdUpdateCannotSilentlyCompleteMigration() {
        withReleasedVersion34Database(
            SUPPRESSED_ROW_ID_UPDATE_DATABASE
        ) { database ->
            insertWalletAndChain(database)
            insertAsset(database, FUNDED_SYMBOL)
            database.execSQL(
                """
                CREATE TRIGGER ignore_asset_sort_index_update
                BEFORE UPDATE OF sortIndex ON assets
                BEGIN
                    SELECT RAISE(IGNORE);
                END
                """.trimIndent()
            )

            assertThrows(
                AssetsOrderMigrationIntegrityException::class.java
            ) {
                AssetsOrderMigration(exactSingleRowLimits()).migrate(database)
            }

            assertTrue(database.hasColumn("assets", "sortIndex"))
            assertTrue(database.hasColumn("assets", "enabled"))
            assertFalse(database.hasTemporaryNormalizationLookup())
            assertEquals(
                0,
                database.singleInt(
                    "SELECT sortIndex FROM assets WHERE tokenSymbol = ?",
                    FUNDED_SYMBOL
                )
            )
        }
    }

    private fun assertPreflightFailureBeforeSchemaMutation(
        database: SupportSQLiteDatabase,
        limits: AssetsOrderMigrationLimits = exactSingleRowLimits()
    ) {
        assertThrows(AssetsOrderMigrationIntegrityException::class.java) {
            AssetsOrderMigration(limits).migrate(database)
        }
        assertFalse(database.hasColumn("assets", "sortIndex"))
        assertFalse(database.hasColumn("assets", "enabled"))
        assertFalse(database.hasTemporaryNormalizationLookup())
    }

    private fun exactSingleRowLimits() = AssetsOrderMigrationLimits(
        maxWalletRows = 1,
        maxAssetRows = 1,
        maxTokenRows = 1,
        maxChainAssetRows = 1,
        maxChainRows = 1,
        maxModelsPerWallet = 1
    )

    private fun replaceTokensWithoutPrimaryKey(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL(
            "CREATE TEMP TABLE saved_tokens AS SELECT * FROM tokens"
        )
        database.execSQL("DROP TABLE tokens")
        database.execSQL(
            """
            CREATE TABLE tokens(
                symbol TEXT NOT NULL,
                dollarRate TEXT,
                recentRateChange TEXT
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO tokens(symbol, dollarRate, recentRateChange)
            SELECT symbol, dollarRate, recentRateChange FROM saved_tokens
            """.trimIndent()
        )
        database.execSQL("DROP TABLE temp.saved_tokens")
    }

    private fun replaceChainsWithoutPrimaryKey(
        database: SupportSQLiteDatabase
    ) {
        database.setForeignKeyConstraintsEnabled(false)
        try {
            database.execSQL(
                "CREATE TEMP TABLE saved_chains AS SELECT * FROM chains"
            )
            database.execSQL("DROP TABLE chains")
            database.execSQL(
                """
                CREATE TABLE chains(
                    id TEXT NOT NULL,
                    parentId TEXT,
                    name TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    prefix INTEGER NOT NULL,
                    isEthereumBased INTEGER NOT NULL,
                    isTestNet INTEGER NOT NULL,
                    hasCrowdloans INTEGER NOT NULL,
                    url TEXT,
                    overridesCommon INTEGER,
                    staking_url TEXT,
                    staking_type TEXT,
                    history_url TEXT,
                    history_type TEXT,
                    crowdloans_url TEXT,
                    crowdloans_type TEXT
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO chains(
                    id,
                    parentId,
                    name,
                    icon,
                    prefix,
                    isEthereumBased,
                    isTestNet,
                    hasCrowdloans,
                    url,
                    overridesCommon,
                    staking_url,
                    staking_type,
                    history_url,
                    history_type,
                    crowdloans_url,
                    crowdloans_type
                )
                SELECT
                    id,
                    parentId,
                    name,
                    icon,
                    prefix,
                    isEthereumBased,
                    isTestNet,
                    hasCrowdloans,
                    url,
                    overridesCommon,
                    staking_url,
                    staking_type,
                    history_url,
                    history_type,
                    crowdloans_url,
                    crowdloans_type
                FROM saved_chains
                """.trimIndent()
            )
            database.execSQL("DROP TABLE temp.saved_chains")
        } finally {
            database.setForeignKeyConstraintsEnabled(true)
        }
    }

    private fun insertUnusedTokenWithOversizedPayload(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL(
            """
            INSERT INTO tokens(symbol, dollarRate, recentRateChange)
            VALUES(?, zeroblob($UNUSED_REGISTRY_PAYLOAD_BYTES), '0')
            """.trimIndent(),
            arrayOf(UNUSED_TOKEN_SYMBOL)
        )
    }

    private fun insertUnusedChainWithOversizedPayload(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL(
            """
            INSERT INTO chains(
                id,
                parentId,
                name,
                icon,
                prefix,
                isEthereumBased,
                isTestNet,
                hasCrowdloans,
                url,
                overridesCommon,
                staking_url,
                staking_type,
                history_url,
                history_type,
                crowdloans_url,
                crowdloans_type
            ) VALUES(
                ?,
                NULL,
                zeroblob($UNUSED_REGISTRY_PAYLOAD_BYTES),
                'unused-icon',
                0,
                0,
                2,
                0,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL
            )
            """.trimIndent(),
            arrayOf(UNUSED_CHAIN_ID)
        )
    }

    /**
     * Starts from the immutable, provenance-checked production v30 database
     * and applies the released SQL-only edges that produce the v34 tables.
     * The 31 -> 32 edge only transforms encrypted preferences and public-key
     * values; this fixture has no rows yet, so it cannot affect the v34 schema.
     */
    private fun <T> withReleasedVersion34Database(
        databaseName: String,
        action: (SupportSQLiteDatabase) -> T
    ): T {
        createdDatabases += databaseName
        ReleasedV200DatabaseFixture.install(context, databaseName)
        val callback = object : SupportSQLiteOpenHelper.Callback(
            ReleasedV200DatabaseFixture.DATABASE_VERSION
        ) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("The frozen released database fixture was not installed")
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                error("Unexpected fixture upgrade from $oldVersion to $newVersion")
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
            MigrateTablesToV2_30_31.migrate(database)
            MigrateTablesToV2_32_33.migrate(database)
            AddChainExplorersTable_33_34.migrate(database)
            database.version = 34
            action(database)
        } finally {
            openHelper.close()
        }
    }

    private fun insertWalletAndChain(database: SupportSQLiteDatabase) {
        insertWallet(database, META_ID, "First")
        database.execSQL(
            """
            INSERT INTO chains(
                id,
                parentId,
                name,
                icon,
                prefix,
                isEthereumBased,
                isTestNet,
                hasCrowdloans,
                url,
                overridesCommon,
                staking_url,
                staking_type,
                history_url,
                history_type,
                crowdloans_url,
                crowdloans_type
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                CHAIN_ID,
                null,
                "Released schema chain 雪",
                "chain-icon",
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        )
    }

    private fun insertWallet(
        database: SupportSQLiteDatabase,
        metaId: Long,
        label: String
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
                name,
                isSelected,
                position
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                metaId,
                ByteArray(32) { 0x11 },
                "SR25519",
                ByteArray(32) { 0x22 },
                null,
                null,
                "$label released schema wallet",
                1,
                0
            )
        )
    }

    private fun insertAsset(
        database: SupportSQLiteDatabase,
        symbol: String,
        free: String = "1",
        reserved: String = "0",
        dollarRate: String? = "1",
        precision: Int = 12,
        chainAssetId: String = symbol,
        metaId: Long = META_ID,
        accountId: ByteArray = ACCOUNT_ID,
        registerTokenAndChainAsset: Boolean = true
    ) {
        if (registerTokenAndChainAsset) {
            database.execSQL(
                """
                INSERT INTO tokens(symbol, dollarRate, recentRateChange)
                VALUES(?, ?, '0')
                """.trimIndent(),
                arrayOf(symbol, dollarRate)
            )
            insertChainAsset(database, chainAssetId, precision)
        }
        database.execSQL(
            """
            INSERT INTO assets(
                tokenSymbol,
                chainId,
                accountId,
                metaId,
                freeInPlanks,
                reservedInPlanks,
                miscFrozenInPlanks,
                feeFrozenInPlanks,
                bondedInPlanks,
                redeemableInPlanks,
                unbondingInPlanks
            ) VALUES(?, ?, ?, ?, ?, ?, '0', '0', '0', '0', '0')
            """.trimIndent(),
            arrayOf(
                symbol,
                CHAIN_ID,
                accountId,
                metaId,
                free,
                reserved
            )
        )
    }

    private fun insertChainAsset(
        database: SupportSQLiteDatabase,
        symbol: String,
        precision: Int
    ) {
        database.execSQL(
            """
            INSERT INTO chain_assets(
                id,
                chainId,
                name,
                icon,
                priceId,
                staking,
                precision,
                priceProviders,
                nativeChainId
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                symbol,
                CHAIN_ID,
                "Asset $symbol",
                "asset-icon",
                null,
                "ALLOWED",
                precision,
                null,
                null
            )
        )
    }

    private fun SupportSQLiteDatabase.hasColumn(
        tableName: String,
        columnName: String
    ): Boolean {
        check(tableName.all { it == '_' || it.isLetterOrDigit() })
        return query(
            "SELECT 1 FROM pragma_table_info('$tableName') " +
                "WHERE name = ? LIMIT 1",
            arrayOf<Any?>(columnName)
        ).use { it.moveToFirst() }
    }

    private fun SupportSQLiteDatabase.hasTemporaryNormalizationLookup(): Boolean {
        return query(
            "SELECT 1 FROM sqlite_temp_master " +
                "WHERE type = 'table' AND " +
                "name = '_assets_order_normalized_chain_assets' LIMIT 1"
        ).use { it.moveToFirst() }
    }

    private fun SupportSQLiteDatabase.singleInt(
        sql: String,
        vararg bindArgs: Any?
    ): Int {
        return query(sql, bindArgs).use { cursor ->
            check(cursor.moveToFirst())
            val value = cursor.getInt(0)
            check(!cursor.moveToNext())
            value
        }
    }

    private companion object {
        const val META_ID = 34_001L
        const val SECOND_META_ID = 34_002L
        const val CHAIN_ID = "released-v34-chain"
        const val FUNDED_SYMBOL = "FUNDED"
        const val EMPTY_SYMBOL = "EMPTY"
        const val ADVERSARIAL_CHAIN_ASSET_ROWS = 32
        const val MAX_BALANCE_BYTES = 128
        const val MAX_CHAIN_NAME_BYTES = 512
        const val UNUSED_REGISTRY_PAYLOAD_BYTES = 8 * 1024 * 1024
        const val UNUSED_TOKEN_SYMBOL = "UNUSED"
        const val UNUSED_CHAIN_ID = "unused-v34-chain"

        const val EXACT_LIMIT_DATABASE = "assets-order-v34-exact-limit"
        const val ASSET_LIMIT_PLUS_ONE_DATABASE =
            "assets-order-v34-limit-plus-one"
        const val MODEL_LIMIT_PLUS_ONE_DATABASE =
            "assets-order-v34-model-limit-plus-one"
        const val JOIN_AMPLIFICATION_DATABASE =
            "assets-order-v34-join-amplification"
        const val CASE_NORMALIZATION_DATABASE =
            "assets-order-v34-case-normalization"
        const val NORMALIZATION_COLLISION_DATABASE =
            "assets-order-v34-normalization-collision"
        const val MULTI_WALLET_DATABASE =
            "assets-order-v34-multi-wallet"
        const val OVERSIZED_BALANCE_DATABASE =
            "assets-order-v34-oversized-balance"
        const val OVERSIZED_CHAIN_NAME_DATABASE =
            "assets-order-v34-oversized-chain-name"
        const val INVALID_PRECISION_DATABASE =
            "assets-order-v34-invalid-precision"
        const val INVALID_TESTNET_DATABASE =
            "assets-order-v34-invalid-testnet"
        const val MISSING_TOKEN_PRIMARY_KEY_DATABASE =
            "assets-order-v34-missing-token-primary-key"
        const val MISSING_CHAIN_PRIMARY_KEY_DATABASE =
            "assets-order-v34-missing-chain-primary-key"
        const val MISSING_ASSET_META_ID_INDEX_DATABASE =
            "assets-order-v34-missing-asset-meta-id-index"
        const val UNUSED_REGISTRY_PAYLOAD_DATABASE =
            "assets-order-v34-unused-registry-payload"
        const val UNUSED_TOKEN_LIMIT_DATABASE =
            "assets-order-v34-unused-token-limit"
        const val UNUSED_CHAIN_LIMIT_DATABASE =
            "assets-order-v34-unused-chain-limit"
        const val SUPPRESSED_ROW_ID_UPDATE_DATABASE =
            "assets-order-v34-suppressed-rowid-update"

        val ACCOUNT_ID = ByteArray(32) { index -> (index + 1).toByte() }
        val SECOND_ACCOUNT_ID =
            ByteArray(32) { index -> (index + 65).toByte() }
    }
}
