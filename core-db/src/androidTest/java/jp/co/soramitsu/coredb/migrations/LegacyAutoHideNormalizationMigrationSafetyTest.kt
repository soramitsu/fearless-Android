package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV374DatabaseFixture
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Real-SQLite coverage for [LegacyAutoHideNormalizationMigration]. Each row mirrors a state that
 * a released build could leave behind; the expectation is the explicit preference after 81.
 */
class LegacyAutoHideNormalizationMigrationSafetyTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val createdDatabases = mutableSetOf<String>()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation,
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun deleteDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
        createdDatabases.clear()
    }

    @Test
    fun emptyHiddenRowsBecomeAutoWhileFundedAndUnknownHidesStayHidden() {
        val databaseName = databaseName("legacy-auto-hide-80-to-81")
        val before = helper.createDatabase(databaseName, PREVIOUS_VERSION).use { database ->
            database.insertVersion80Chain()
            VERSION_80_ROWS.forEach { row -> database.insertAsset(row) }
            database.rowsWithoutEnabled()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            NORMALIZED_VERSION,
            true,
            LegacyAutoHideNormalizationMigration
        ).use { database ->
            database.assertExpectedPreferences(VERSION_80_ROWS)
            assertEquals(before, database.rowsWithoutEnabled())

            // A repeated run, for example after an interrupted first launch, matches nothing.
            LegacyAutoHideNormalizationMigration.migrate(database)
            database.assertExpectedPreferences(VERSION_80_ROWS)
            assertEquals(before, database.rowsWithoutEnabled())
        }
    }

    @Test
    fun released374AutoHidesBecomeAutoThroughProductionOpen() {
        val databaseName = databaseName("legacy-auto-hide-v71-production-open")
        ReleasedV374DatabaseFixture.install(context, databaseName)
        val before = withVersion71Fixture(databaseName) { database ->
            database.insertVersion71Chain()
            RELEASED_374_ROWS.forEach { row -> database.insertAsset(row) }
            database.rowsWithoutEnabled()
        }

        val preferences = HashMapEncryptedPreferences()
        val roomDatabase = AppDatabase.create(
            context = context,
            databaseName = databaseName,
            storeV1 = SecretStoreV1Impl(preferences),
            storeV2 = SecretStoreV2(preferences),
            encryptedPreferences = preferences,
            substrateSecretStore = SubstrateSecretStore(preferences),
            ethereumSecretStore = EthereumSecretStore(preferences)
        )
        try {
            val database = roomDatabase.openHelper.writableDatabase
            assertEquals(APP_DATABASE_VERSION, database.version)
            database.assertExpectedPreferences(RELEASED_374_ROWS)
            assertEquals(before, database.rowsWithoutEnabled())
        } finally {
            roomDatabase.close()
        }
    }

    private fun databaseName(name: String): String = name.also { createdDatabases += it }

    private fun <T> withVersion71Fixture(databaseName: String, block: (SupportSQLiteDatabase) -> T): T {
        val callback = object :
            SupportSQLiteOpenHelper.Callback(ReleasedV374DatabaseFixture.DATABASE_VERSION) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("The frozen 3.7.4 fixture was not installed")
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                error("Unexpected fixture setup migration from $oldVersion to $newVersion")
            }
        }
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
        return try {
            openHelper.writableDatabase.let { database ->
                database.setForeignKeyConstraintsEnabled(true)
                assertEquals(ReleasedV374DatabaseFixture.DATABASE_VERSION, database.version)
                block(database)
            }
        } finally {
            openHelper.close()
        }
    }

    private fun SupportSQLiteDatabase.insertVersion80Chain() {
        execSQL(
            """
            INSERT INTO chains(
                id, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans,
                supportStakingPool, isEthereumChain, isChainlinkProvider, supportNft,
                isUsesAppId, ecosystem
            ) VALUES(?, 'Auto-hide chain', 'icon', 0, 0, 0, 0, 0, 0, 0, 0, 0, 'Substrate')
            """.trimIndent(),
            arrayOf<Any>(CHAIN_ID)
        )
    }

    private fun SupportSQLiteDatabase.insertVersion71Chain() {
        execSQL(
            """
            INSERT INTO chains(
                id, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans,
                supportStakingPool, isEthereumChain, isChainlinkProvider, supportNft,
                isUsesAppId
            ) VALUES(?, 'Auto-hide chain', 'icon', 0, 0, 0, 0, 0, 0, 0, 0, 0)
            """.trimIndent(),
            arrayOf<Any>(CHAIN_ID)
        )
    }

    private fun SupportSQLiteDatabase.insertAsset(row: AssetRow) {
        execSQL(
            """
            INSERT INTO assets(
                id, chainId, accountId, metaId, tokenPriceId,
                freeInPlanks, reservedInPlanks, miscFrozenInPlanks, feeFrozenInPlanks,
                bondedInPlanks, redeemableInPlanks, unbondingInPlanks,
                sortIndex, enabled, markedNotNeed, chainAccountName, status
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                row.id,
                CHAIN_ID,
                row.accountId,
                row.metaId,
                "price-${row.id}",
                row.free,
                row.reserved,
                row.miscFrozen,
                row.feeFrozen,
                row.bonded,
                row.redeemable,
                row.unbonding,
                row.sortIndex,
                row.enabled,
                if (row.markedNotNeed) 1 else 0,
                "account-${row.id}",
                "SYNCED"
            )
        )
    }

    private fun SupportSQLiteDatabase.assertExpectedPreferences(rows: List<AssetRow>) {
        assertEquals(rows.size, singleInt("SELECT COUNT(*) FROM assets"))
        rows.forEach { row -> assertEquals(row.id, row.expectedEnabled, enabledOf(row)) }
    }

    private fun SupportSQLiteDatabase.enabledOf(row: AssetRow): Int? = query(
        "SELECT enabled FROM assets WHERE id = ? AND chainId = ? AND metaId = ?",
        arrayOf<Any>(row.id, CHAIN_ID, row.metaId)
    ).use { cursor ->
        assertTrue(row.id, cursor.moveToFirst())
        val enabled = if (cursor.isNull(0)) null else cursor.getInt(0)
        assertFalse(row.id, cursor.moveToNext())
        enabled
    }

    private fun SupportSQLiteDatabase.rowsWithoutEnabled(): List<List<String?>> = query(
        // Every column except `enabled`, with SQLite storage classes, must survive unchanged.
        """
        SELECT id, chainId, hex(accountId), metaId, tokenPriceId,
            quote(freeInPlanks), quote(reservedInPlanks),
            quote(miscFrozenInPlanks), quote(feeFrozenInPlanks),
            quote(bondedInPlanks), quote(redeemableInPlanks), quote(unbondingInPlanks),
            sortIndex, markedNotNeed, chainAccountName, status
        FROM assets ORDER BY metaId, id
        """.trimIndent()
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    (0 until cursor.columnCount).map { index ->
                        if (cursor.isNull(index)) null else cursor.getString(index)
                    }
                )
            }
        }
    }

    private fun SupportSQLiteDatabase.singleInt(sql: String): Int = query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private class AssetRow(
        val id: String,
        val expectedEnabled: Int?,
        val enabled: Int?,
        val free: Any? = null,
        val reserved: Any? = null,
        val miscFrozen: Any? = null,
        val feeFrozen: Any? = null,
        val bonded: Any? = null,
        val redeemable: Any? = null,
        val unbonding: Any? = null,
        val markedNotNeed: Boolean = false,
        val metaId: Long = WALLET_META_ID,
        val accountId: ByteArray = WALLET_ACCOUNT_ID,
        val sortIndex: Int = Int.MAX_VALUE
    )

    private companion object {
        const val PREVIOUS_VERSION = 80
        const val NORMALIZED_VERSION = 81
        const val CHAIN_ID = "legacy-auto-hide-chain"
        const val WALLET_META_ID = 7L
        const val SECOND_WALLET_META_ID = 8L
        const val HIDDEN = 0
        const val SHOWN = 1
        val WALLET_ACCOUNT_ID = ByteArray(32) { index -> (index + 1).toByte() }
        val GENERIC_ACCOUNT_ID = ByteArray(0)

        val VERSION_80_ROWS = listOf(
            loadedZero("zero-hidden", enabled = HIDDEN, expectedEnabled = null),
            AssetRow("unloaded-hidden", expectedEnabled = null, enabled = HIDDEN),
            AssetRow("integer-zero-hidden", expectedEnabled = null, enabled = HIDDEN, free = 0),
            AssetRow(
                "generic-zero-hidden",
                expectedEnabled = null,
                enabled = HIDDEN,
                free = "0",
                accountId = GENERIC_ACCOUNT_ID
            ),
            AssetRow(
                "second-wallet-zero-hidden",
                expectedEnabled = null,
                enabled = HIDDEN,
                free = "0",
                metaId = SECOND_WALLET_META_ID
            ),
            AssetRow("funded-hidden", expectedEnabled = HIDDEN, enabled = HIDDEN, free = "1000"),
            AssetRow(
                "huge-funded-hidden",
                expectedEnabled = HIDDEN,
                enabled = HIDDEN,
                free = "123456789012345678901234567890"
            ),
            AssetRow(
                "reserved-only-hidden",
                expectedEnabled = HIDDEN,
                enabled = HIDDEN,
                free = "0",
                reserved = "5"
            ),
            AssetRow(
                "frozen-only-hidden",
                expectedEnabled = HIDDEN,
                enabled = HIDDEN,
                free = "0",
                miscFrozen = "3",
                feeFrozen = "0"
            ),
            AssetRow(
                "bonded-only-hidden",
                expectedEnabled = HIDDEN,
                enabled = HIDDEN,
                free = "0",
                bonded = "7"
            ),
            AssetRow(
                "redeemable-only-hidden",
                expectedEnabled = HIDDEN,
                enabled = HIDDEN,
                redeemable = "2"
            ),
            AssetRow(
                "unbonding-only-hidden",
                expectedEnabled = HIDDEN,
                enabled = HIDDEN,
                unbonding = "9"
            ),
            AssetRow("error-hidden", expectedEnabled = HIDDEN, enabled = HIDDEN, free = "-1"),
            AssetRow("garbage-hidden", expectedEnabled = HIDDEN, enabled = HIDDEN, free = "abc"),
            AssetRow("empty-text-hidden", expectedEnabled = HIDDEN, enabled = HIDDEN, free = ""),
            AssetRow("noncanonical-zero-hidden", expectedEnabled = HIDDEN, enabled = HIDDEN, free = "00"),
            AssetRow(
                "not-needed-hidden",
                expectedEnabled = HIDDEN,
                enabled = HIDDEN,
                free = "0",
                markedNotNeed = true
            ),
            loadedZero("shown-zero", enabled = SHOWN, expectedEnabled = SHOWN),
            AssetRow("shown-funded", expectedEnabled = SHOWN, enabled = SHOWN, free = "10"),
            loadedZero("auto-zero", enabled = null, expectedEnabled = null),
            AssetRow("auto-funded", expectedEnabled = null, enabled = null, free = "10")
        )

        /**
         * A funded 3.7.4 wallet after its first sync: the funded and popular rows shown, every
         * empty row hidden automatically, and one funded token the user hid deliberately.
         */
        val RELEASED_374_ROWS = listOf(
            AssetRow(
                "funded-shown",
                expectedEnabled = SHOWN,
                enabled = SHOWN,
                free = "25000000000",
                reserved = "0",
                miscFrozen = "0",
                feeFrozen = "0",
                sortIndex = 0
            ),
            loadedZero("popular-zero-shown", enabled = SHOWN, expectedEnabled = SHOWN),
            loadedZero("zero-auto-hidden", enabled = HIDDEN, expectedEnabled = null),
            AssetRow("unloaded-auto-hidden", expectedEnabled = null, enabled = HIDDEN),
            AssetRow(
                "funded-deliberately-hidden",
                expectedEnabled = HIDDEN,
                enabled = HIDDEN,
                free = "990000",
                reserved = "0",
                miscFrozen = "0",
                feeFrozen = "0"
            ),
            AssetRow("error-hidden", expectedEnabled = HIDDEN, enabled = HIDDEN, free = "-1")
        )

        /** Loaded zero balance as written by the 4.2.0 balance loaders before the auto-hide ran. */
        fun loadedZero(
            id: String,
            enabled: Int?,
            expectedEnabled: Int?
        ) = AssetRow(
            id = id,
            expectedEnabled = expectedEnabled,
            enabled = enabled,
            free = "0",
            reserved = "0",
            miscFrozen = "0",
            feeFrozen = "0"
        )
    }
}
