package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The repair the 76 -> 77 migration runs before its preflight (addendum K10):
 * an unknown cache or user-settings layout, a missing cache table, other
 * indexes and dangling registry caches are repaired and the production
 * builder opens the database; the wallet identity tables still fail closed
 * with nothing changed.
 */
class RebuildableTableRepairTest {

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
    fun deleteDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
    }

    @Test
    fun releasedVersion76DatabaseIsNotWritten() {
        createVersion76(CANONICAL).use { database ->
            database.insertRegistryAndWallet()
            val schemaBefore = database.schema()
            val changesBefore = database.totalChanges()

            val report = database.repairWithoutEnforcement()

            assertFalse(report.changedSchema)
            assertEquals(emptyList<String>(), report.skipped)
            assertEquals(emptyMap<String, Int>(), report.danglingCacheRowsDeleted)
            assertEquals(schemaBefore, database.schema())
            assertEquals(changesBefore, database.totalChanges())
        }
    }

    @Test
    fun unknownCacheLayoutIsRebuiltWithItsRowsAndOpens() {
        createVersion76(UNKNOWN_CACHE_LAYOUT).use { database ->
            database.insertRegistryAndWallet()
            database.rebuildAs(
                "chain_nodes",
                "CREATE TABLE `chain_nodes` (`url` TEXT NOT NULL, `chainId` TEXT NOT NULL, " +
                    "`isDefault` INTEGER NOT NULL DEFAULT 1, `isActive` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, PRIMARY KEY(`chainId`, `url`), " +
                    "FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX `index_chain_nodes_url` ON `chain_nodes` (`url`)"
            )
        }
        val nodesBefore = rows(UNKNOWN_CACHE_LAYOUT, NODES)

        assertEquals(APP_DATABASE_VERSION, openProduction(UNKNOWN_CACHE_LAYOUT))

        assertEquals(nodesBefore, rows(UNKNOWN_CACHE_LAYOUT, NODES))
        withDatabase(UNKNOWN_CACHE_LAYOUT) { database ->
            assertEquals(
                listOf("chainId", "url", "name", "isActive", "isDefault"),
                database.strings("SELECT name FROM pragma_table_info('chain_nodes') ORDER BY cid")
            )
            assertEquals(
                listOf("index_chain_nodes_chainId"),
                database.strings("SELECT name FROM pragma_index_list('chain_nodes') WHERE origin = 'c'")
            )
        }
    }

    @Test
    fun unknownUserSettingsLayoutKeepsEveryRowByName() {
        createVersion76(UNKNOWN_USER_LAYOUT).use { database ->
            database.insertRegistryAndWallet()
            database.rebuildAs(
                "assets",
                "CREATE TABLE `assets` (`metaId` INTEGER NOT NULL, `id` TEXT NOT NULL, " +
                    "`chainId` TEXT NOT NULL, `accountId` BLOB NOT NULL, `enabled` INTEGER DEFAULT 1, " +
                    "`sortIndex` INTEGER, `markedNotNeed` INTEGER NOT NULL DEFAULT 0, " +
                    "`tokenPriceId` TEXT, `freeInPlanks` TEXT, `reservedInPlanks` TEXT, " +
                    "`miscFrozenInPlanks` TEXT, `feeFrozenInPlanks` TEXT, `bondedInPlanks` TEXT, " +
                    "`redeemableInPlanks` TEXT, `unbondingInPlanks` TEXT, `chainAccountName` TEXT, " +
                    "`status` TEXT, PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`), " +
                    "FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
                "CREATE INDEX `index_assets_metaId` ON `assets` (`metaId`)"
            )
            // A row the old layout allowed without an order keeps its place.
            database.execSQL(
                "INSERT INTO assets(metaId, id, chainId, accountId, enabled, sortIndex, freeInPlanks) " +
                    "VALUES(?, 'unordered', ?, ?, 0, NULL, '3')",
                arrayOf<Any?>(META_ID, CHAIN_ID, ACCOUNT_ID)
            )
        }
        val assetsBefore = rows(UNKNOWN_USER_LAYOUT, ASSETS)
            .map { row -> if (row["id"] == "unordered") row + ("sortIndex" to "0") else row }

        assertEquals(APP_DATABASE_VERSION, openProduction(UNKNOWN_USER_LAYOUT))

        assertEquals(assetsBefore, rows(UNKNOWN_USER_LAYOUT, ASSETS))
        assertEquals(3, assetsBefore.size)
    }

    @Test
    fun missingCacheTableIsCreatedAndDanglingCachesAreDeleted() {
        createVersion76(MISSING_CACHE_TABLE).use { database ->
            database.insertRegistryAndWallet()
            database.setForeignKeyConstraintsEnabled(false)
            database.execSQL("DROP TABLE nomis_wallet_score")
            database.execSQL(
                "INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault) " +
                    "VALUES('dropped-network', 'wss://dropped.invalid', 'Dropped', 1, 1)"
            )
            database.execSQL(
                "INSERT INTO chain_explorers(chainId, type, types, url) " +
                    "VALUES('dropped-network', 'subscan', 'account', 'https://dropped.invalid')"
            )
            database.execSQL(
                "INSERT INTO chain_assets(id, symbol, chainId, icon, staking, precision) " +
                    "VALUES('native', 'DRP', 'dropped-network', 'icon', 'NONE', 12)"
            )
            database.execSQL(
                "INSERT INTO favorite_chains(metaId, chainId, isFavorite) VALUES(?, 'dropped-network', 1)",
                arrayOf<Any?>(META_ID)
            )
            database.execSQL(
                "INSERT INTO assets(id, chainId, accountId, metaId, sortIndex, enabled, markedNotNeed) " +
                    "VALUES('native', 'dropped-network', ?, ?, 7, 0, 0)",
                arrayOf<Any?>(ACCOUNT_ID, META_ID)
            )

            val report = database.repairWithoutEnforcement()

            assertEquals(listOf("nomis_wallet_score"), report.created)
            assertEquals(
                mapOf("chain_nodes" to 1, "chain_assets" to 1, "chain_explorers" to 1),
                report.danglingCacheRowsDeleted
            )
            WalletSecretIntegrityQueryPreflight.requireReadable(database)
            assertEquals(
                listOf(listOf("assets", "chains"), listOf("favorite_chains", "chains")),
                database.foreignKeyViolations()
            )
        }
    }

    @Test
    fun chainAccountOnNetworkTheRegistryDroppedIsKeptAndOpens() {
        createVersion76(DROPPED_NETWORK_ACCOUNT).use { database ->
            database.insertRegistryAndWallet()
            database.setForeignKeyConstraintsEnabled(false)
            database.execSQL(
                "INSERT INTO chain_accounts(metaId, chainId, publicKey, accountId, cryptoType, name, initialized) " +
                    "VALUES(?, 'dropped-network', ?, ?, 'SR25519', 'On a dropped network', 1)",
                arrayOf<Any?>(META_ID, ACCOUNT_ID, ACCOUNT_ID)
            )
        }
        val accountsBefore = rows(DROPPED_NETWORK_ACCOUNT, CHAIN_ACCOUNTS)

        assertEquals(APP_DATABASE_VERSION, openProduction(DROPPED_NETWORK_ACCOUNT))

        assertEquals(accountsBefore, rows(DROPPED_NETWORK_ACCOUNT, CHAIN_ACCOUNTS))
        assertEquals(1, accountsBefore.size)
    }

    @Test
    fun chainAccountWithoutItsWalletStillFailsClosedAndChangesNothing() {
        createVersion76(ORPHAN_CHAIN_ACCOUNT).use { database ->
            database.insertRegistryAndWallet()
            database.setForeignKeyConstraintsEnabled(false)
            database.execSQL(
                "INSERT INTO chain_accounts(metaId, chainId, publicKey, accountId, cryptoType, name, initialized) " +
                    "VALUES(?, ?, ?, ?, 'SR25519', 'No wallet row', 1)",
                arrayOf<Any?>(META_ID + 1, CHAIN_ID, ACCOUNT_ID, ACCOUNT_ID)
            )
            database.execSQL(
                "INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault) " +
                    "VALUES('dropped-network', 'wss://dropped.invalid', 'Dropped', 1, 1)"
            )
        }

        assertFailsClosedUnchanged(ORPHAN_CHAIN_ACCOUNT)
    }

    @Test
    fun unknownWalletIdentityLayoutStillFailsClosedAndChangesNothing() {
        createVersion76(UNKNOWN_IDENTITY_LAYOUT).use { database ->
            database.insertRegistryAndWallet()
            database.rebuildAs(
                "meta_accounts",
                "CREATE TABLE `meta_accounts` (`substratePublicKey` BLOB, `substrateCryptoType` TEXT, " +
                    "`substrateAccountId` BLOB, `ethereumPublicKey` BLOB, `ethereumAddress` BLOB, " +
                    "`tonPublicKey` BLOB, `isSelected` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL, `isBackedUp` INTEGER NOT NULL, `googleBackupAddress` TEXT, " +
                    "`initialized` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
                "CREATE INDEX `index_meta_accounts_substrateAccountId` ON `meta_accounts` (`substrateAccountId`)",
                "CREATE INDEX `index_meta_accounts_ethereumAddress` ON `meta_accounts` (`ethereumAddress`)",
                "CREATE INDEX `index_meta_accounts_tonPublicKey` ON `meta_accounts` (`tonPublicKey`)"
            )
            // A cache repair in the same upgrade must roll back with it.
            database.rebuildAs(
                "token_price",
                "CREATE TABLE `token_price` (`fiatSymbol` TEXT NOT NULL, `priceId` TEXT NOT NULL, " +
                    "`fiatRate` TEXT, `recentRateChange` TEXT, PRIMARY KEY(`priceId`))"
            )
        }

        assertFailsClosedUnchanged(UNKNOWN_IDENTITY_LAYOUT)
    }

    @Test
    fun enforcedForeignKeysLeaveUnknownLayoutsToTheStrictCheck() {
        createVersion76(ENFORCED_FOREIGN_KEYS).use { database ->
            database.insertRegistryAndWallet()
            database.rebuildAs(
                "chains",
                "CREATE TABLE `chains` (`name` TEXT NOT NULL, `id` TEXT NOT NULL, `paraId` TEXT, " +
                    "`parentId` TEXT, `rank` INTEGER, `minSupportedVersion` TEXT, `icon` TEXT NOT NULL, " +
                    "`prefix` INTEGER NOT NULL, `isEthereumBased` INTEGER NOT NULL, `isTestNet` INTEGER NOT NULL, " +
                    "`hasCrowdloans` INTEGER NOT NULL, `supportStakingPool` INTEGER NOT NULL, " +
                    "`isEthereumChain` INTEGER NOT NULL, `isChainlinkProvider` INTEGER NOT NULL, " +
                    "`supportNft` INTEGER NOT NULL, `isUsesAppId` INTEGER NOT NULL, `identityChain` TEXT, " +
                    "`ecosystem` TEXT NOT NULL, `androidMinAppVersion` TEXT, `remoteAssetsSource` TEXT, " +
                    "`tonBridgeUrl` TEXT, `xcm` TEXT, `staking_url` TEXT, `staking_type` TEXT, " +
                    "`history_url` TEXT, `history_type` TEXT, `crowdloans_url` TEXT, " +
                    "`crowdloans_type` TEXT, PRIMARY KEY(`id`))"
            )
            val schemaBefore = database.schema()
            database.setForeignKeyConstraintsEnabled(true)

            val report = RebuildableTableRepair.repair(database)

            assertEquals(listOf("chains"), report.skipped)
            assertEquals(schemaBefore, database.schema())
            assertEquals(1, database.strings("SELECT url FROM chain_nodes").size)
        }
    }

    private fun assertFailsClosedUnchanged(databaseName: String) {
        val schemaBefore = withDatabase(databaseName) { it.schema() }
        val rowsBefore = USER_TABLES.associateWith { rows(databaseName, it) }
        val preferences = HashMapEncryptedPreferences()

        val failure = runCatching { openProduction(databaseName, preferences) }.exceptionOrNull()

        assertTrue(
            "Expected a wallet-identity failure, got $failure",
            generateSequence(failure) { it.cause }.any { it is WalletPublicIdentityIntegrityException }
        )
        withDatabase(databaseName) { database ->
            assertEquals(76, database.version)
            assertEquals(schemaBefore, database.schema())
        }
        assertEquals(rowsBefore, USER_TABLES.associateWith { rows(databaseName, it) })
        assertFalse(preferences.hasKeyWithPrefix(""))
    }

    private fun createVersion76(name: String): SupportSQLiteDatabase {
        createdDatabases += name
        return helper.createDatabase(name, 76)
    }

    private fun openProduction(
        databaseName: String,
        preferences: HashMapEncryptedPreferences = HashMapEncryptedPreferences()
    ): Int {
        var database: AppDatabase? = null
        try {
            database = AppDatabase.create(
                context = context,
                databaseName = databaseName,
                storeV1 = SecretStoreV1Impl(preferences),
                storeV2 = SecretStoreV2(preferences),
                encryptedPreferences = preferences,
                substrateSecretStore = SubstrateSecretStore(preferences),
                ethereumSecretStore = EthereumSecretStore(preferences)
            )
            return database.openHelper.writableDatabase.version
        } finally {
            database?.close()
        }
    }

    /** Opens the file at the version it already has, so nothing upgrades it. */
    private fun <T> withDatabase(databaseName: String, action: (SupportSQLiteDatabase) -> T): T {
        val version = SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { it.version }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = error("Missing test database")
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                        error("Unexpected upgrade")
                })
                .build()
        )
        return try {
            action(helper.writableDatabase)
        } finally {
            helper.close()
        }
    }

    private fun rows(databaseName: String, sql: String): List<Map<String, String?>> =
        withDatabase(databaseName) { database ->
            database.query(sql).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            cursor.columnNames.indices.associate { index ->
                                cursor.getColumnName(index) to when {
                                    cursor.isNull(index) -> null
                                    cursor.getType(index) == android.database.Cursor.FIELD_TYPE_BLOB ->
                                        cursor.getBlob(index).joinToString("") { "%02x".format(it) }
                                    else -> cursor.getString(index)
                                }
                            }
                        )
                    }
                }
            }
        }

    private fun SupportSQLiteDatabase.repairWithoutEnforcement(): RebuildableTableRepairReport {
        setForeignKeyConstraintsEnabled(false)
        beginTransaction()
        try {
            return RebuildableTableRepair.repair(this).also { setTransactionSuccessful() }
        } finally {
            endTransaction()
        }
    }

    /** Recreates [table] as [createSql], copying every row by column name. */
    private fun SupportSQLiteDatabase.rebuildAs(table: String, createSql: String, vararg indexSql: String) {
        val columns = strings("SELECT name FROM pragma_table_info('$table') ORDER BY cid")
            .joinToString { "`$it`" }
        // Copy, drop, then rename the copy: renaming the original would
        // rewrite every child's REFERENCES clause to the old table.
        setForeignKeyConstraintsEnabled(false)
        beginTransaction()
        try {
            execSQL(createSql.replaceFirst("`$table`", "`_unknown_$table`"))
            execSQL("INSERT INTO `_unknown_$table` ($columns) SELECT $columns FROM `$table`")
            execSQL("DROP TABLE `$table`")
            execSQL("ALTER TABLE `_unknown_$table` RENAME TO `$table`")
            indexSql.forEach(::execSQL)
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun SupportSQLiteDatabase.insertRegistryAndWallet() {
        execSQL(
            "INSERT INTO meta_accounts(id, substratePublicKey, substrateCryptoType, substrateAccountId, " +
                "name, isSelected, position, isBackedUp, initialized) VALUES(?, ?, 'SR25519', ?, 'Repair', 1, 0, 1, 1)",
            arrayOf<Any?>(META_ID, ACCOUNT_ID, ACCOUNT_ID)
        )
        execSQL(
            "INSERT INTO chains(id, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans, " +
                "supportStakingPool, isEthereumChain, isChainlinkProvider, supportNft, isUsesAppId, ecosystem) " +
                "VALUES(?, 'Repair chain', 'icon', 0, 0, 0, 0, 0, 0, 0, 0, 0, 'Substrate')",
            arrayOf<Any?>(CHAIN_ID)
        )
        execSQL(
            "INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault) VALUES(?, 'wss://repair.invalid', 'Node', 1, 0)",
            arrayOf<Any?>(CHAIN_ID)
        )
        execSQL(
            "INSERT INTO assets(id, chainId, accountId, metaId, freeInPlanks, sortIndex, enabled, markedNotNeed) " +
                "VALUES('native', ?, ?, ?, '9', 2, 0, 0)",
            arrayOf<Any?>(CHAIN_ID, ACCOUNT_ID, META_ID)
        )
        execSQL(
            "INSERT INTO assets(id, chainId, accountId, metaId, freeInPlanks, sortIndex, enabled, markedNotNeed) " +
                "VALUES('token', ?, ?, ?, NULL, 5, NULL, 1)",
            arrayOf<Any?>(CHAIN_ID, ACCOUNT_ID, META_ID)
        )
    }

    private fun SupportSQLiteDatabase.schema(): List<List<String?>> =
        query("SELECT type, name, tbl_name, sql FROM sqlite_master ORDER BY type, name").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0..3).map { if (cursor.isNull(it)) null else cursor.getString(it) })
            }
        }

    private fun SupportSQLiteDatabase.totalChanges(): Long =
        query("SELECT total_changes()").use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

    private fun SupportSQLiteDatabase.strings(sql: String): List<String> =
        query(sql).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun SupportSQLiteDatabase.foreignKeyViolations(): List<List<String>> =
        query("PRAGMA foreign_key_check").use { cursor ->
            val childColumn = cursor.getColumnIndexOrThrow("table")
            val parentColumn = cursor.getColumnIndexOrThrow("parent")
            buildList {
                while (cursor.moveToNext()) {
                    add(listOf(cursor.getString(childColumn), cursor.getString(parentColumn)))
                }
            }.sortedWith(compareBy({ it[0] }, { it[1] }))
        }

    private companion object {
        const val META_ID = 7_610L
        const val CHAIN_ID = "repair-chain"
        val ACCOUNT_ID = ByteArray(32) { (it + 3).toByte() }

        const val CANONICAL = "repair-v76-canonical"
        const val UNKNOWN_CACHE_LAYOUT = "repair-v76-unknown-cache-layout"
        const val UNKNOWN_USER_LAYOUT = "repair-v76-unknown-user-layout"
        const val MISSING_CACHE_TABLE = "repair-v76-missing-cache-table"
        const val DROPPED_NETWORK_ACCOUNT = "repair-v76-dropped-network-account"
        const val ORPHAN_CHAIN_ACCOUNT = "repair-v76-orphan-chain-account"
        const val UNKNOWN_IDENTITY_LAYOUT = "repair-v76-unknown-identity-layout"
        const val ENFORCED_FOREIGN_KEYS = "repair-v76-enforced-foreign-keys"

        const val NODES = "SELECT chainId, url, name, isActive, isDefault FROM chain_nodes ORDER BY chainId, url"
        const val ASSETS = "SELECT id, chainId, accountId, metaId, freeInPlanks, sortIndex, enabled, markedNotNeed " +
            "FROM assets ORDER BY metaId, chainId, id"
        const val CHAIN_ACCOUNTS = "SELECT metaId, chainId, publicKey, accountId, cryptoType, name, initialized " +
            "FROM chain_accounts ORDER BY metaId, chainId"
        val USER_TABLES = listOf(
            "SELECT * FROM meta_accounts ORDER BY id",
            "SELECT * FROM chain_accounts ORDER BY metaId, chainId",
            ASSETS,
            "SELECT * FROM token_price ORDER BY priceId",
            NODES
        )
    }
}
