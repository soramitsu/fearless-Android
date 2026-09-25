package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV374DatabaseFixture
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Physical-schema regression coverage for every released chains and
 * meta_accounts order accepted by the v71 and v76 production guards.
 *
 * The DDL below is deliberately frozen in this test instead of being generated
 * from the production contract. That makes a contract edit prove itself
 * against independently described, physically created SQLite tables.
 */
@Suppress(
    "LargeClass",
    "LongMethod",
    "MagicNumber",
    "NestedBlockDepth",
    "TooManyFunctions"
)
class ReleasedSchemaPhysicalCohortTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val createdDatabases = mutableSetOf<String>()

    @get:Rule
    val migrationHelper = MigrationTestHelper(
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
    fun everyReleasedVersion71ChainCohortMigratesThroughVersion76() {
        FROZEN_V71_CHAIN_COHORTS.forEachIndexed { index, cohort ->
            val databaseName = "released-physical-v71-chain-$index"
            withVersion71Fixture(databaseName) { database ->
                insertChain(database, V71_CHAIN_VALUES)
                rebuildTable(
                    database = database,
                    tableName = CHAINS,
                    cohort = cohort,
                    tablePrimaryKey = listOf("id")
                )

                assertExactPhysicalTable(database, CHAINS, cohort)
                assertChainRowPreserved(database, expectedEcosystem = null)
                TonUpgradeSqlPreflight.requireSafeToMutate(database)

                val preferences = ReadOnlyEmptyPreferences()
                migrateVersion71Through76(database, preferences)
                val expectedV76 = cohort.upgradedFrom71To76()

                assertExactPhysicalTable(database, CHAINS, expectedV76)
                assertChainRowPreserved(
                    database,
                    expectedEcosystem = "Ethereum"
                )
                WalletSecretIntegrityQueryPreflight.requireReadable(database)
                WalletSecretIntegrityMigration(preferences).migrate(database)
            }
        }
    }

    @Test
    fun everyReleasedVersion71MetaAccountCohortMigratesThroughVersion76() {
        FROZEN_V71_META_ACCOUNT_COHORTS.forEachIndexed { index, cohort ->
            val databaseName = "released-physical-v71-meta-$index"
            withVersion71Fixture(databaseName) { database ->
                insertMetaAccount(database, V71_META_ACCOUNT_VALUES)
                rebuildMetaAccounts(
                    database = database,
                    cohort = cohort,
                    includeTonIndex = false
                )

                assertExactPhysicalTable(database, META_ACCOUNTS, cohort)
                assertMetaAccountRowPreserved(
                    database = database,
                    includeTonPublicKey = false
                )
                TonUpgradeSqlPreflight.requireSafeToMutate(database)

                val preferences = ReadOnlyEmptyPreferences()
                migrateVersion71Through76(database, preferences)

                assertExactPhysicalTable(
                    database,
                    META_ACCOUNTS,
                    FROZEN_V76_META_ACCOUNT_COHORTS.first()
                )
                assertMetaAccountRowPreserved(
                    database = database,
                    includeTonPublicKey = true
                )
                WalletSecretIntegrityQueryPreflight.requireReadable(database)
                WalletSecretIntegrityMigration(preferences).migrate(database)
            }
        }
    }

    @Test
    fun everyReleasedVersion76ChainCohortPassesIntegrityMigration() {
        FROZEN_V76_CHAIN_COHORTS.forEachIndexed { index, cohort ->
            val databaseName = "released-physical-v76-chain-$index"
            withFreshVersion76(databaseName) { database ->
                insertChain(database, V76_CHAIN_VALUES)
                rebuildTable(
                    database = database,
                    tableName = CHAINS,
                    cohort = cohort,
                    tablePrimaryKey = listOf("id")
                )

                assertExactPhysicalTable(database, CHAINS, cohort)
                assertChainRowPreserved(
                    database,
                    expectedEcosystem = "EthereumBased"
                )
                WalletSecretIntegrityQueryPreflight.requireReadable(database)
                WalletSecretIntegrityMigration(
                    ReadOnlyEmptyPreferences()
                ).migrate(database)
            }
        }
    }

    @Test
    fun everyReleasedVersion76MetaAccountCohortPassesIntegrityMigration() {
        FROZEN_V76_META_ACCOUNT_COHORTS.forEachIndexed { index, cohort ->
            val databaseName = "released-physical-v76-meta-$index"
            withFreshVersion76(databaseName) { database ->
                insertMetaAccount(database, V76_META_ACCOUNT_VALUES)
                rebuildMetaAccounts(
                    database = database,
                    cohort = cohort,
                    includeTonIndex = true
                )

                assertExactPhysicalTable(database, META_ACCOUNTS, cohort)
                assertMetaAccountRowPreserved(
                    database = database,
                    includeTonPublicKey = true
                )
                WalletSecretIntegrityQueryPreflight.requireReadable(database)
                WalletSecretIntegrityMigration(
                    ReadOnlyEmptyPreferences()
                ).migrate(database)
            }
        }
    }

    @Test
    fun unshippedVersion71ChainOrderIsRejectedBeforeMigration() {
        withVersion71Fixture(UNSHIPPED_V71_DATABASE) { database ->
            rebuildTable(
                database = database,
                tableName = CHAINS,
                cohort = UNSHIPPED_V71_CHAIN_COHORT,
                tablePrimaryKey = listOf("id")
            )
            assertExactPhysicalTable(
                database,
                CHAINS,
                UNSHIPPED_V71_CHAIN_COHORT
            )

            val failure = assertThrows(
                TonUpgradeSqlIntegrityException::class.java
            ) {
                TonUpgradeSqlPreflight.requireSafeToMutate(database)
            }
            assertTrue(
                failure.message.orEmpty().contains(
                    "chains has incompatible ordered column definitions"
                )
            )
        }
    }

    @Test
    fun unshippedVersion76MetaAccountOrderIsRejected() {
        withFreshVersion76(UNSHIPPED_V76_DATABASE) { database ->
            rebuildMetaAccounts(
                database = database,
                cohort = UNSHIPPED_V76_META_ACCOUNT_COHORT,
                includeTonIndex = true
            )
            assertExactPhysicalTable(
                database,
                META_ACCOUNTS,
                UNSHIPPED_V76_META_ACCOUNT_COHORT
            )

            val failure = assertThrows(
                WalletPublicIdentityIntegrityException::class.java
            ) {
                WalletSecretIntegrityQueryPreflight.requireReadable(database)
            }
            assertTrue(
                failure.message.orEmpty().contains(
                    "meta_accounts has incompatible ordered column definitions"
                )
            )
        }
    }

    private fun migrateVersion71Through76(
        database: SupportSQLiteDatabase,
        preferences: EncryptedPreferences
    ) {
        database.beginTransaction()
        try {
            TonMigration(preferences).migrate(database)
            Migration_72_73.migrate(database)
            Migration_73_74.migrate(database)
            Migration_74_75.migrate(database)
            Migration_75_76.migrate(database)
            database.version = 76
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun rebuildMetaAccounts(
        database: SupportSQLiteDatabase,
        cohort: FrozenTableCohort,
        includeTonIndex: Boolean
    ) {
        rebuildTable(
            database = database,
            tableName = META_ACCOUNTS,
            cohort = cohort,
            inlineAutoIncrementPrimaryKey = "id",
            indexes = buildList {
                add(
                    "CREATE INDEX `index_meta_accounts_substrateAccountId` " +
                        "ON `meta_accounts` (`substrateAccountId`)"
                )
                add(
                    "CREATE INDEX `index_meta_accounts_ethereumAddress` " +
                        "ON `meta_accounts` (`ethereumAddress`)"
                )
                if (includeTonIndex) {
                    add(
                        "CREATE INDEX `index_meta_accounts_tonPublicKey` " +
                            "ON `meta_accounts` (`tonPublicKey`)"
                    )
                }
            }
        )
    }

    private fun rebuildTable(
        database: SupportSQLiteDatabase,
        tableName: String,
        cohort: FrozenTableCohort,
        tablePrimaryKey: List<String> = emptyList(),
        inlineAutoIncrementPrimaryKey: String? = null,
        indexes: List<String> = emptyList()
    ) {
        val temporaryTable = "_physical_cohort_$tableName"
        val quotedColumns = cohort.columns.joinToString {
            "`${it.name}`"
        }

        database.setForeignKeyConstraintsEnabled(false)
        try {
            database.beginTransaction()
            try {
                database.execSQL("DROP TABLE IF EXISTS `$temporaryTable`")
                database.execSQL(
                    createTableSql(
                        tableName = temporaryTable,
                        cohort = cohort,
                        tablePrimaryKey = tablePrimaryKey,
                        inlineAutoIncrementPrimaryKey =
                        inlineAutoIncrementPrimaryKey
                    )
                )
                database.execSQL(
                    "INSERT INTO `$temporaryTable` ($quotedColumns) " +
                        "SELECT $quotedColumns FROM `$tableName`"
                )
                database.execSQL("DROP TABLE `$tableName`")
                database.execSQL(
                    "ALTER TABLE `$temporaryTable` RENAME TO `$tableName`"
                )
                indexes.forEach(database::execSQL)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        } finally {
            database.setForeignKeyConstraintsEnabled(true)
        }
        assertEquals(
            "Foreign-key damage while rebuilding $tableName as ${cohort.label}",
            0,
            database.singleInt(
                "SELECT COUNT(*) FROM pragma_foreign_key_check"
            )
        )
    }

    private fun createTableSql(
        tableName: String,
        cohort: FrozenTableCohort,
        tablePrimaryKey: List<String>,
        inlineAutoIncrementPrimaryKey: String?
    ): String {
        val definitions = cohort.columns.map { column ->
            buildString {
                append("`${column.name}` ${column.type}")
                if (column.name == inlineAutoIncrementPrimaryKey) {
                    append(" PRIMARY KEY AUTOINCREMENT")
                }
                if (column.notNull) append(" NOT NULL")
                column.defaultSql?.let {
                    append(" DEFAULT $it")
                }
            }
        }.toMutableList()
        if (tablePrimaryKey.isNotEmpty()) {
            definitions += tablePrimaryKey.joinToString(
                prefix = "PRIMARY KEY(",
                postfix = ")"
            ) { "`$it`" }
        }
        return definitions.joinToString(
            prefix = "CREATE TABLE `$tableName` (",
            postfix = ")"
        )
    }

    private fun assertExactPhysicalTable(
        database: SupportSQLiteDatabase,
        tableName: String,
        cohort: FrozenTableCohort
    ) {
        database.query(
            "SELECT cid, name, type, `notnull`, dflt_value, pk " +
                "FROM pragma_table_info('$tableName') ORDER BY cid ASC"
        ).use { cursor ->
            cohort.columns.forEachIndexed { index, expected ->
                assertTrue(
                    "Missing $tableName.${expected.name} for ${cohort.label}",
                    cursor.moveToNext()
                )
                assertEquals(index, cursor.getInt(0))
                assertEquals(expected.name, cursor.getString(1))
                assertEquals(expected.type, cursor.getString(2))
                assertEquals(if (expected.notNull) 1 else 0, cursor.getInt(3))
                if (expected.defaultSql == null) {
                    assertTrue(cursor.isNull(4))
                } else {
                    assertEquals(expected.defaultSql, cursor.getString(4))
                }
                assertEquals(
                    expected.primaryKeyPosition,
                    cursor.getInt(5)
                )
            }
            assertFalse(
                "Unexpected trailing $tableName column for ${cohort.label}",
                cursor.moveToNext()
            )
        }
    }

    private fun insertChain(
        database: SupportSQLiteDatabase,
        values: Map<String, Any?>
    ) {
        val columns = values.keys.toList()
        val quotedColumns = columns.joinToString { "`$it`" }
        val placeholders = columns.joinToString { "?" }
        database.execSQL(
            "INSERT INTO chains ($quotedColumns) VALUES ($placeholders)",
            columns.map(values::getValue).toTypedArray()
        )
    }

    private fun insertMetaAccount(
        database: SupportSQLiteDatabase,
        values: Map<String, Any?>
    ) {
        val columns = values.keys.toList()
        val quotedColumns = columns.joinToString { "`$it`" }
        val placeholders = columns.joinToString { "?" }
        database.execSQL(
            "INSERT INTO meta_accounts ($quotedColumns) VALUES ($placeholders)",
            columns.map(values::getValue).toTypedArray()
        )
    }

    private fun assertChainRowPreserved(
        database: SupportSQLiteDatabase,
        expectedEcosystem: String?
    ) {
        val ecosystemProjection = if (expectedEcosystem == null) {
            ""
        } else {
            ", ecosystem"
        }
        database.query(
            "SELECT name, parentId, paraId, rank, remoteAssetsSource, " +
                "history_url$ecosystemProjection FROM chains WHERE id = ?",
            arrayOf(CHAIN_ID)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(CHAIN_NAME, cursor.getString(0))
            assertEquals(CHAIN_PARENT_ID, cursor.getString(1))
            assertEquals(CHAIN_PARA_ID, cursor.getString(2))
            assertEquals(CHAIN_RANK, cursor.getInt(3))
            assertEquals(CHAIN_REMOTE_ASSETS_SOURCE, cursor.getString(4))
            assertEquals(CHAIN_HISTORY_URL, cursor.getString(5))
            expectedEcosystem?.let {
                assertEquals(it, cursor.getString(6))
            }
            assertFalse(cursor.moveToNext())
        }
    }

    private fun assertMetaAccountRowPreserved(
        database: SupportSQLiteDatabase,
        includeTonPublicKey: Boolean
    ) {
        val tonProjection = if (includeTonPublicKey) {
            ", tonPublicKey"
        } else {
            ""
        }
        database.query(
            "SELECT substratePublicKey, substrateCryptoType, " +
                "substrateAccountId, name, isSelected, position, " +
                "isBackedUp, googleBackupAddress, initialized$tonProjection " +
                "FROM meta_accounts WHERE id = ?",
            arrayOf(META_ID)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertArrayEquals(SUBSTRATE_PUBLIC_KEY, cursor.getBlob(0))
            assertEquals("SR25519", cursor.getString(1))
            assertArrayEquals(SUBSTRATE_ACCOUNT_ID, cursor.getBlob(2))
            assertEquals(META_NAME, cursor.getString(3))
            assertEquals(1, cursor.getInt(4))
            assertEquals(META_POSITION, cursor.getInt(5))
            assertEquals(1, cursor.getInt(6))
            assertEquals(META_BACKUP_ADDRESS, cursor.getString(7))
            assertEquals(1, cursor.getInt(8))
            if (includeTonPublicKey) {
                assertTrue(cursor.isNull(9))
            }
            assertFalse(cursor.moveToNext())
        }
    }

    private fun <T> withVersion71Fixture(
        databaseName: String,
        action: (SupportSQLiteDatabase) -> T
    ): T {
        createdDatabases += databaseName
        ReleasedV374DatabaseFixture.install(context, databaseName)
        val callback = object : SupportSQLiteOpenHelper.Callback(71) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("The frozen released v71 fixture was not installed")
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
            action(database)
        } finally {
            openHelper.close()
        }
    }

    private fun <T> withFreshVersion76(
        databaseName: String,
        action: (SupportSQLiteDatabase) -> T
    ): T {
        createdDatabases += databaseName
        return migrationHelper.createDatabase(databaseName, 76).use {
            it.setForeignKeyConstraintsEnabled(true)
            action(it)
        }
    }

    private fun SupportSQLiteDatabase.singleInt(sql: String): Int {
        return query(sql).use { cursor ->
            check(cursor.moveToFirst())
            val value = cursor.getInt(0)
            check(!cursor.moveToNext())
            value
        }
    }

    private class ReadOnlyEmptyPreferences : EncryptedPreferences {

        override fun putEncryptedString(field: String, value: String) {
            error("A schema-cohort migration unexpectedly wrote preferences")
        }

        override fun getDecryptedString(field: String): String? = null

        override fun hasKey(field: String): Boolean = false

        override fun hasKeyWithPrefix(prefix: String): Boolean = false

        override fun keysWithPrefixes(
            prefixes: Set<String>,
            maxResultCount: Int,
            maxKeyBytes: Int,
            maxTotalKeyBytes: Int,
            failOnOversizedMatch: Boolean
        ): Set<String> = emptySet()

        override fun removeKey(field: String) {
            error("A schema-cohort migration unexpectedly removed preferences")
        }

        override fun replaceEncryptedStringsDurably(
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>
        ) {
            error("A schema-cohort migration unexpectedly replaced preferences")
        }

        override fun quarantineEncryptedStringDurably(
            sourceKey: String,
            quarantineKey: String,
            expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean {
            error("A schema-cohort migration unexpectedly quarantined preferences")
        }

        override fun requireDurableStorageHealthy() = Unit
    }

    private data class FrozenColumn(
        val name: String,
        val type: String,
        val notNull: Boolean = false,
        val primaryKeyPosition: Int = 0,
        val defaultSql: String? = null
    )

    private data class FrozenTableCohort(
        val label: String,
        val columns: List<FrozenColumn>
    ) {
        init {
            require(columns.isNotEmpty())
            require(columns.map(FrozenColumn::name).toSet().size == columns.size)
        }

        fun upgradedFrom71To76(): FrozenTableCohort {
            return FrozenTableCohort(
                label = "$label-upgraded-to-v76",
                columns = columns + V76_UPGRADE_SUFFIX_COLUMNS
            )
        }
    }

    private companion object {
        const val CHAINS = "chains"
        const val META_ACCOUNTS = "meta_accounts"
        const val CHAIN_ID = "physical-cohort-chain"
        const val CHAIN_NAME = "Physical cohort chain ' \" \uD83D\uDD10"
        const val CHAIN_PARENT_ID = "physical-parent"
        const val CHAIN_PARA_ID = "2048"
        const val CHAIN_RANK = 17
        const val CHAIN_REMOTE_ASSETS_SOURCE = "remote://cohort"
        const val CHAIN_HISTORY_URL = "https://history.invalid/?q='\""
        const val META_ID = 71_076L
        const val META_NAME = "Physical cohort wallet ' \" \uD83D\uDD10"
        const val META_POSITION = 23
        const val META_BACKUP_ADDRESS = "cohort+'\"@example.invalid"
        const val UNSHIPPED_V71_DATABASE =
            "released-physical-v71-unshipped"
        const val UNSHIPPED_V76_DATABASE =
            "released-physical-v76-unshipped"

        val SUBSTRATE_PUBLIC_KEY =
            ByteArray(32) { index -> (index + 1).toByte() }
        val SUBSTRATE_ACCOUNT_ID =
            ByteArray(32) { index -> (index + 65).toByte() }

        val V71_CHAIN_VALUES: Map<String, Any?> = linkedMapOf(
            "id" to CHAIN_ID,
            "parentId" to CHAIN_PARENT_ID,
            "paraId" to CHAIN_PARA_ID,
            "rank" to CHAIN_RANK,
            "name" to CHAIN_NAME,
            "minSupportedVersion" to "1.2.3",
            "icon" to "cohort-icon",
            "prefix" to 42,
            "isEthereumBased" to 0,
            "isTestNet" to 1,
            "hasCrowdloans" to 1,
            "supportStakingPool" to 1,
            "isEthereumChain" to 1,
            "isChainlinkProvider" to 1,
            "supportNft" to 1,
            "isUsesAppId" to 1,
            "identityChain" to "identity-chain",
            "remoteAssetsSource" to CHAIN_REMOTE_ASSETS_SOURCE,
            "staking_url" to "https://staking.invalid",
            "staking_type" to "subscan",
            "history_url" to CHAIN_HISTORY_URL,
            "history_type" to "subquery",
            "crowdloans_url" to "https://crowdloans.invalid",
            "crowdloans_type" to "subquery"
        )

        val V76_CHAIN_VALUES: Map<String, Any?> = linkedMapOf(
            *V71_CHAIN_VALUES.entries.map { it.key to it.value }.toTypedArray(),
            "ecosystem" to "EthereumBased",
            "androidMinAppVersion" to "9.9.9",
            "tonBridgeUrl" to "https://ton.invalid",
            "xcm" to "{\"version\":3}"
        )

        val V71_META_ACCOUNT_VALUES: Map<String, Any?> = linkedMapOf(
            "id" to META_ID,
            "substratePublicKey" to SUBSTRATE_PUBLIC_KEY,
            "substrateCryptoType" to "SR25519",
            "substrateAccountId" to SUBSTRATE_ACCOUNT_ID,
            "ethereumPublicKey" to null,
            "ethereumAddress" to null,
            "name" to META_NAME,
            "isSelected" to 1,
            "position" to META_POSITION,
            "isBackedUp" to 1,
            "googleBackupAddress" to META_BACKUP_ADDRESS,
            "initialized" to 1
        )

        val V76_META_ACCOUNT_VALUES: Map<String, Any?> = linkedMapOf(
            *V71_META_ACCOUNT_VALUES.entries
                .map { it.key to it.value }
                .toTypedArray(),
            "tonPublicKey" to null
        )

        val CHAIN_DEFINITIONS = mapOf(
            "id" to FrozenColumn(
                "id",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            "paraId" to FrozenColumn("paraId", "TEXT"),
            "parentId" to FrozenColumn("parentId", "TEXT"),
            "rank" to FrozenColumn("rank", "INTEGER"),
            "name" to FrozenColumn("name", "TEXT", notNull = true),
            "minSupportedVersion" to
                FrozenColumn("minSupportedVersion", "TEXT"),
            "icon" to FrozenColumn("icon", "TEXT", notNull = true),
            "prefix" to FrozenColumn("prefix", "INTEGER", notNull = true),
            "isEthereumBased" to
                FrozenColumn("isEthereumBased", "INTEGER", notNull = true),
            "isTestNet" to
                FrozenColumn("isTestNet", "INTEGER", notNull = true),
            "hasCrowdloans" to
                FrozenColumn("hasCrowdloans", "INTEGER", notNull = true),
            "supportStakingPool" to
                FrozenColumn("supportStakingPool", "INTEGER", notNull = true),
            "isEthereumChain" to
                FrozenColumn("isEthereumChain", "INTEGER", notNull = true),
            "isChainlinkProvider" to FrozenColumn(
                "isChainlinkProvider",
                "INTEGER",
                notNull = true
            ),
            "supportNft" to
                FrozenColumn("supportNft", "INTEGER", notNull = true),
            "isUsesAppId" to
                FrozenColumn("isUsesAppId", "INTEGER", notNull = true),
            "identityChain" to FrozenColumn("identityChain", "TEXT"),
            "remoteAssetsSource" to
                FrozenColumn("remoteAssetsSource", "TEXT"),
            "ecosystem" to
                FrozenColumn("ecosystem", "TEXT", notNull = true),
            "androidMinAppVersion" to
                FrozenColumn("androidMinAppVersion", "TEXT"),
            "tonBridgeUrl" to FrozenColumn("tonBridgeUrl", "TEXT"),
            "xcm" to FrozenColumn("xcm", "TEXT"),
            "staking_url" to FrozenColumn("staking_url", "TEXT"),
            "staking_type" to FrozenColumn("staking_type", "TEXT"),
            "history_url" to FrozenColumn("history_url", "TEXT"),
            "history_type" to FrozenColumn("history_type", "TEXT"),
            "crowdloans_url" to FrozenColumn("crowdloans_url", "TEXT"),
            "crowdloans_type" to FrozenColumn("crowdloans_type", "TEXT")
        )

        val V71_META_DEFINITIONS = mapOf(
            "id" to FrozenColumn(
                "id",
                "INTEGER",
                notNull = true,
                primaryKeyPosition = 1
            ),
            "substratePublicKey" to FrozenColumn(
                "substratePublicKey",
                "BLOB",
                notNull = true
            ),
            "substrateCryptoType" to FrozenColumn(
                "substrateCryptoType",
                "TEXT",
                notNull = true
            ),
            "substrateAccountId" to FrozenColumn(
                "substrateAccountId",
                "BLOB",
                notNull = true
            ),
            "ethereumPublicKey" to
                FrozenColumn("ethereumPublicKey", "BLOB"),
            "ethereumAddress" to FrozenColumn("ethereumAddress", "BLOB"),
            "name" to FrozenColumn("name", "TEXT", notNull = true),
            "isSelected" to
                FrozenColumn("isSelected", "INTEGER", notNull = true),
            "position" to
                FrozenColumn("position", "INTEGER", notNull = true),
            "isBackedUp" to
                FrozenColumn("isBackedUp", "INTEGER", notNull = true),
            "googleBackupAddress" to
                FrozenColumn("googleBackupAddress", "TEXT"),
            "initialized" to
                FrozenColumn("initialized", "INTEGER", notNull = true)
        )

        val V76_META_DEFINITIONS = mapOf(
            "id" to V71_META_DEFINITIONS.getValue("id"),
            "substratePublicKey" to
                FrozenColumn("substratePublicKey", "BLOB"),
            "substrateCryptoType" to
                FrozenColumn("substrateCryptoType", "TEXT"),
            "substrateAccountId" to
                FrozenColumn("substrateAccountId", "BLOB"),
            "ethereumPublicKey" to
                FrozenColumn("ethereumPublicKey", "BLOB"),
            "ethereumAddress" to FrozenColumn("ethereumAddress", "BLOB"),
            "tonPublicKey" to FrozenColumn("tonPublicKey", "BLOB"),
            "name" to FrozenColumn("name", "TEXT", notNull = true),
            "isSelected" to
                FrozenColumn("isSelected", "INTEGER", notNull = true),
            "position" to
                FrozenColumn("position", "INTEGER", notNull = true),
            "isBackedUp" to
                FrozenColumn("isBackedUp", "INTEGER", notNull = true),
            "googleBackupAddress" to
                FrozenColumn("googleBackupAddress", "TEXT"),
            "initialized" to
                FrozenColumn("initialized", "INTEGER", notNull = true)
        )

        val CHAIN_CORE_COLUMNS = listOf(
            "name",
            "minSupportedVersion",
            "icon",
            "prefix",
            "isEthereumBased",
            "isTestNet",
            "hasCrowdloans",
            "supportStakingPool"
        )

        val CHAIN_URL_COLUMNS = listOf(
            "staking_url",
            "staking_type",
            "history_url",
            "history_type",
            "crowdloans_url",
            "crowdloans_type"
        )

        val V76_UPGRADE_SUFFIX_COLUMNS = listOf(
            CHAIN_DEFINITIONS.getValue("ecosystem").copy(
                defaultSql = "'Substrate'"
            ),
            CHAIN_DEFINITIONS.getValue("androidMinAppVersion").copy(
                defaultSql = "NULL"
            ),
            CHAIN_DEFINITIONS.getValue("tonBridgeUrl").copy(
                defaultSql = "NULL"
            ),
            CHAIN_DEFINITIONS.getValue("xcm").copy(defaultSql = "NULL")
        )

        val LATE_CHAIN_DEFAULTS_C1_TO_C3 = mapOf(
            "isChainlinkProvider" to "0",
            "supportNft" to "0",
            "isUsesAppId" to "0",
            "identityChain" to "NULL",
            "remoteAssetsSource" to "NULL"
        )

        val FROZEN_V71_CHAIN_COHORTS = listOf(
            v71ChainCohort(
                label = "v71-chain-c0",
                identity = listOf("id", "parentId"),
                features = emptyList(),
                appended = listOf(
                    "isEthereumChain",
                    "rank",
                    "paraId",
                    "isChainlinkProvider",
                    "supportNft",
                    "isUsesAppId",
                    "identityChain",
                    "remoteAssetsSource"
                ),
                defaults = mapOf(
                    "isEthereumChain" to "0",
                    "isChainlinkProvider" to "0",
                    "supportNft" to "0",
                    "isUsesAppId" to "0",
                    "identityChain" to "NULL",
                    "remoteAssetsSource" to "NULL"
                )
            ),
            v71ChainCohort(
                "v71-chain-c1",
                listOf("id", "parentId"),
                listOf("isEthereumChain"),
                listOf(
                    "rank",
                    "paraId",
                    "isChainlinkProvider",
                    "supportNft",
                    "isUsesAppId",
                    "identityChain",
                    "remoteAssetsSource"
                ),
                LATE_CHAIN_DEFAULTS_C1_TO_C3
            ),
            v71ChainCohort(
                "v71-chain-c2",
                listOf("id", "parentId", "rank"),
                listOf("isEthereumChain"),
                listOf(
                    "paraId",
                    "isChainlinkProvider",
                    "supportNft",
                    "isUsesAppId",
                    "identityChain",
                    "remoteAssetsSource"
                ),
                LATE_CHAIN_DEFAULTS_C1_TO_C3
            ),
            v71ChainCohort(
                "v71-chain-c3",
                listOf("id", "paraId", "parentId", "rank"),
                listOf("isEthereumChain"),
                listOf(
                    "isChainlinkProvider",
                    "supportNft",
                    "isUsesAppId",
                    "identityChain",
                    "remoteAssetsSource"
                ),
                LATE_CHAIN_DEFAULTS_C1_TO_C3
            ),
            v71ChainCohort(
                "v71-chain-c4",
                listOf("id", "paraId", "parentId", "rank"),
                listOf("isEthereumChain", "isChainlinkProvider"),
                listOf(
                    "supportNft",
                    "isUsesAppId",
                    "identityChain",
                    "remoteAssetsSource"
                ),
                mapOf(
                    "supportNft" to "0",
                    "isUsesAppId" to "0",
                    "identityChain" to "NULL",
                    "remoteAssetsSource" to "NULL"
                )
            ),
            v71ChainCohort(
                "v71-chain-c5",
                listOf("id", "paraId", "parentId", "rank"),
                listOf(
                    "isEthereumChain",
                    "isChainlinkProvider",
                    "supportNft"
                ),
                listOf(
                    "isUsesAppId",
                    "identityChain",
                    "remoteAssetsSource"
                ),
                mapOf(
                    "isUsesAppId" to "0",
                    "identityChain" to "NULL",
                    "remoteAssetsSource" to "NULL"
                )
            ),
            v71ChainCohort(
                "v71-chain-c6",
                listOf("id", "paraId", "parentId", "rank"),
                listOf(
                    "isEthereumChain",
                    "isChainlinkProvider",
                    "supportNft",
                    "isUsesAppId"
                ),
                listOf("identityChain", "remoteAssetsSource"),
                mapOf(
                    "identityChain" to "NULL",
                    "remoteAssetsSource" to "NULL"
                )
            ),
            v71ChainCohort(
                "v71-chain-c7",
                listOf("id", "paraId", "parentId", "rank"),
                listOf(
                    "isEthereumChain",
                    "isChainlinkProvider",
                    "supportNft",
                    "isUsesAppId",
                    "identityChain"
                ),
                listOf("remoteAssetsSource"),
                mapOf("remoteAssetsSource" to "NULL")
            ),
            v71ChainCohort(
                "v71-chain-c8",
                listOf("id", "paraId", "parentId", "rank"),
                listOf(
                    "isEthereumChain",
                    "isChainlinkProvider",
                    "supportNft",
                    "isUsesAppId",
                    "identityChain",
                    "remoteAssetsSource"
                ),
                emptyList(),
                emptyMap()
            )
        )

        val FROZEN_V71_META_ACCOUNT_COHORTS = listOf(
            metaCohort(
                "v71-meta-m0",
                listOf(
                    "id",
                    "substratePublicKey",
                    "substrateCryptoType",
                    "substrateAccountId",
                    "ethereumPublicKey",
                    "ethereumAddress",
                    "name",
                    "isSelected",
                    "position",
                    "isBackedUp",
                    "googleBackupAddress",
                    "initialized"
                ),
                V71_META_DEFINITIONS,
                mapOf(
                    "isBackedUp" to "0",
                    "googleBackupAddress" to "NULL",
                    "initialized" to "0"
                )
            ),
            metaCohort(
                "v71-meta-m1",
                listOf(
                    "substratePublicKey",
                    "substrateCryptoType",
                    "substrateAccountId",
                    "ethereumPublicKey",
                    "ethereumAddress",
                    "name",
                    "isSelected",
                    "position",
                    "id",
                    "isBackedUp",
                    "googleBackupAddress",
                    "initialized"
                ),
                V71_META_DEFINITIONS,
                mapOf(
                    "isBackedUp" to "0",
                    "googleBackupAddress" to "NULL",
                    "initialized" to "0"
                )
            ),
            metaCohort(
                "v71-meta-m2",
                listOf(
                    "substratePublicKey",
                    "substrateCryptoType",
                    "substrateAccountId",
                    "ethereumPublicKey",
                    "ethereumAddress",
                    "name",
                    "isSelected",
                    "position",
                    "isBackedUp",
                    "googleBackupAddress",
                    "id",
                    "initialized"
                ),
                V71_META_DEFINITIONS,
                mapOf("initialized" to "0")
            ),
            metaCohort(
                "v71-meta-m3",
                listOf(
                    "substratePublicKey",
                    "substrateCryptoType",
                    "substrateAccountId",
                    "ethereumPublicKey",
                    "ethereumAddress",
                    "name",
                    "isSelected",
                    "position",
                    "isBackedUp",
                    "googleBackupAddress",
                    "initialized",
                    "id"
                ),
                V71_META_DEFINITIONS,
                emptyMap()
            )
        )

        val FROZEN_V76_META_ACCOUNT_COHORTS = listOf(
            metaCohort(
                "v76-meta-ton-upgrade-id-first",
                listOf(
                    "id",
                    "substratePublicKey",
                    "substrateCryptoType",
                    "substrateAccountId",
                    "ethereumPublicKey",
                    "ethereumAddress",
                    "tonPublicKey",
                    "name",
                    "isSelected",
                    "position",
                    "isBackedUp",
                    "googleBackupAddress",
                    "initialized"
                ),
                V76_META_DEFINITIONS,
                emptyMap()
            ),
            metaCohort(
                "v76-meta-fresh-id-last",
                listOf(
                    "substratePublicKey",
                    "substrateCryptoType",
                    "substrateAccountId",
                    "ethereumPublicKey",
                    "ethereumAddress",
                    "tonPublicKey",
                    "name",
                    "isSelected",
                    "position",
                    "isBackedUp",
                    "googleBackupAddress",
                    "initialized",
                    "id"
                ),
                V76_META_DEFINITIONS,
                emptyMap()
            )
        )

        val FROZEN_V76_CHAIN_COHORTS =
            listOf(
                chainCohort(
                    "v76-chain-fresh",
                    listOf(
                        "id",
                        "paraId",
                        "parentId",
                        "rank"
                    ) + CHAIN_CORE_COLUMNS + listOf(
                        "isEthereumChain",
                        "isChainlinkProvider",
                        "supportNft",
                        "isUsesAppId",
                        "identityChain",
                        "ecosystem",
                        "androidMinAppVersion",
                        "remoteAssetsSource",
                        "tonBridgeUrl",
                        "xcm"
                    ) + CHAIN_URL_COLUMNS,
                    emptyMap()
                ),
                chainCohort(
                    "v76-chain-fresh-v73-to-v75",
                    listOf(
                        "id",
                        "paraId",
                        "parentId",
                        "rank"
                    ) + CHAIN_CORE_COLUMNS + listOf(
                        "isEthereumChain",
                        "isChainlinkProvider",
                        "supportNft",
                        "isUsesAppId",
                        "identityChain",
                        "ecosystem",
                        "androidMinAppVersion",
                        "remoteAssetsSource",
                        "tonBridgeUrl"
                    ) + CHAIN_URL_COLUMNS + "xcm",
                    mapOf("xcm" to "NULL")
                ),
                chainCohort(
                    "v76-chain-fresh-v72",
                    listOf(
                        "id",
                        "paraId",
                        "parentId",
                        "rank"
                    ) + CHAIN_CORE_COLUMNS + listOf(
                        "isEthereumChain",
                        "isChainlinkProvider",
                        "supportNft",
                        "isUsesAppId",
                        "identityChain",
                        "ecosystem",
                        "androidMinAppVersion",
                        "remoteAssetsSource"
                    ) + CHAIN_URL_COLUMNS + listOf("tonBridgeUrl", "xcm"),
                    mapOf(
                        "tonBridgeUrl" to "NULL",
                        "xcm" to "NULL"
                    )
                )
            ) + FROZEN_V71_CHAIN_COHORTS.map(
                FrozenTableCohort::upgradedFrom71To76
            )

        val UNSHIPPED_V71_CHAIN_COHORT =
            FROZEN_V71_CHAIN_COHORTS.last().let { shipped ->
                val altered = shipped.columns.toMutableList()
                val nameIndex = altered.indexOfFirst { it.name == "name" }
                val iconIndex = altered.indexOfFirst { it.name == "icon" }
                val originalName = altered[nameIndex]
                altered[nameIndex] = altered[iconIndex]
                altered[iconIndex] = originalName
                FrozenTableCohort("unshipped-v71-name-icon-swap", altered)
            }

        val UNSHIPPED_V76_META_ACCOUNT_COHORT =
            FROZEN_V76_META_ACCOUNT_COHORTS.last().let { shipped ->
                val altered = shipped.columns.toMutableList()
                val nameIndex = altered.indexOfFirst { it.name == "name" }
                val selectedIndex =
                    altered.indexOfFirst { it.name == "isSelected" }
                val originalName = altered[nameIndex]
                altered[nameIndex] = altered[selectedIndex]
                altered[selectedIndex] = originalName
                FrozenTableCohort("unshipped-v76-name-selected-swap", altered)
            }

        fun v71ChainCohort(
            label: String,
            identity: List<String>,
            features: List<String>,
            appended: List<String>,
            defaults: Map<String, String>
        ): FrozenTableCohort {
            return chainCohort(
                label = label,
                order = identity +
                    CHAIN_CORE_COLUMNS +
                    features +
                    CHAIN_URL_COLUMNS +
                    appended,
                defaults = defaults
            )
        }

        fun chainCohort(
            label: String,
            order: List<String>,
            defaults: Map<String, String>
        ): FrozenTableCohort {
            return FrozenTableCohort(
                label = label,
                columns = order.map { name ->
                    CHAIN_DEFINITIONS.getValue(name).copy(
                        defaultSql = defaults[name]
                    )
                }
            )
        }

        fun metaCohort(
            label: String,
            order: List<String>,
            definitions: Map<String, FrozenColumn>,
            defaults: Map<String, String>
        ): FrozenTableCohort {
            return FrozenTableCohort(
                label = label,
                columns = order.map { name ->
                    definitions.getValue(name).copy(
                        defaultSql = defaults[name]
                    )
                }
            )
        }
    }
}
