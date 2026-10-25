package jp.co.soramitsu.coredb.migrations

import android.content.Context
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Upgrade path for the portfolio's live balances. A released build (3.5.1 to 4.2.0) connected a
 * network only for an `enabled = 1` asset, hid every empty asset of a funded wallet with
 * `enabled = 0` and never revealed a later deposit into one of them. Starting from the frozen
 * 3.7.4 database and rows in exactly those states, the production open must leave every shown row
 * and every row that holds funds on a network that keeps a live balance subscription, including
 * the funded row the old build left hidden.
 */
class ReleasedWalletBalanceSyncUpgradeTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val createdDatabases = mutableSetOf<String>()

    @After
    fun deleteDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
        createdDatabases.clear()
    }

    @Test
    fun released374WalletKeepsLiveBalancesForShownAndFundedRowsThroughProductionOpen() {
        val databaseName = "released-374-balance-sync".also { createdDatabases += it }
        ReleasedV374DatabaseFixture.install(context, databaseName)
        withVersion71Fixture(databaseName) { database ->
            RELEASED_374_ROWS.forEach { row ->
                database.insertVersion71Chain(row.chainId)
                database.insertAsset(row)
            }
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
            RELEASED_374_ROWS.forEach { row ->
                assertEquals(row.chainId, row.expectedEnabled, database.enabledOf(row))
            }

            val chainIdsNeedingSync = runBlocking { roomDatabase.assetDao().observeChainIdsNeedingSync().first() }

            assertEquals(
                RELEASED_374_ROWS.filter(AssetRow::needsLiveBalance).map(AssetRow::chainId).toSet(),
                chainIdsNeedingSync
            )
            assertTrue("the deposit 4.2.0 left hidden stays connected", "funded-left-hidden" in chainIdsNeedingSync)
        } finally {
            roomDatabase.close()
        }
    }

    private fun withVersion71Fixture(databaseName: String, block: (SupportSQLiteDatabase) -> Unit) {
        val callback = object :
            SupportSQLiteOpenHelper.Callback(ReleasedV374DatabaseFixture.DATABASE_VERSION) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("The frozen 3.7.4 fixture was not installed")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("Unexpected fixture setup migration from $oldVersion to $newVersion")
            }
        }
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
        try {
            openHelper.writableDatabase.let { database ->
                database.setForeignKeyConstraintsEnabled(true)
                assertEquals(ReleasedV374DatabaseFixture.DATABASE_VERSION, database.version)
                block(database)
            }
        } finally {
            openHelper.close()
        }
    }

    private fun SupportSQLiteDatabase.insertVersion71Chain(chainId: String) {
        execSQL(
            """
            INSERT INTO chains(
                id, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans,
                supportStakingPool, isEthereumChain, isChainlinkProvider, supportNft,
                isUsesAppId
            ) VALUES(?, ?, 'icon', 0, 0, 0, 0, 0, 0, 0, 0, 0)
            """.trimIndent(),
            arrayOf<Any>(chainId, chainId)
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
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, ?, ?, 0, NULL, NULL)
            """.trimIndent(),
            arrayOf<Any?>(
                ASSET_ID,
                row.chainId,
                WALLET_ACCOUNT_ID,
                WALLET_META_ID,
                "price-${row.chainId}",
                row.free,
                row.reserved,
                row.free?.let { "0" },
                row.free?.let { "0" },
                Int.MAX_VALUE,
                row.enabled
            )
        )
    }

    private fun SupportSQLiteDatabase.enabledOf(row: AssetRow): Int? = query(
        "SELECT enabled FROM assets WHERE id = ? AND chainId = ? AND metaId = ?",
        arrayOf<Any>(ASSET_ID, row.chainId, WALLET_META_ID)
    ).use { cursor ->
        assertTrue(row.chainId, cursor.moveToFirst())
        if (cursor.isNull(0)) null else cursor.getInt(0)
    }

    /** One asset on its own network; [chainId] names the state the released build left it in. */
    private class AssetRow(
        val chainId: String,
        val enabled: Int?,
        val expectedEnabled: Int?,
        val free: String?,
        val reserved: String? = free?.let { "0" },
        val needsLiveBalance: Boolean
    )

    private companion object {
        const val ASSET_ID = "asset"
        const val WALLET_META_ID = 7L
        const val HIDDEN = 0
        const val SHOWN = 1
        val WALLET_ACCOUNT_ID = ByteArray(32) { index -> (index + 1).toByte() }

        /**
         * A funded 3.7.4 wallet after its first sync and some use: funded and popular rows shown,
         * every empty row hidden automatically, a deposit into an automatically hidden row that
         * the old build never revealed, a reserved-only holding it hid, and a failed balance load.
         */
        val RELEASED_374_ROWS = listOf(
            AssetRow("funded-shown", enabled = SHOWN, expectedEnabled = SHOWN, free = "25000000000", needsLiveBalance = true),
            AssetRow("popular-zero-shown", enabled = SHOWN, expectedEnabled = SHOWN, free = "0", needsLiveBalance = true),
            AssetRow("zero-auto-hidden", enabled = HIDDEN, expectedEnabled = null, free = "0", needsLiveBalance = false),
            AssetRow("unloaded-auto-hidden", enabled = HIDDEN, expectedEnabled = null, free = null, needsLiveBalance = false),
            AssetRow("funded-left-hidden", enabled = HIDDEN, expectedEnabled = HIDDEN, free = "990000", needsLiveBalance = true),
            AssetRow(
                "reserved-only-left-hidden",
                enabled = HIDDEN,
                expectedEnabled = HIDDEN,
                free = "0",
                reserved = "5",
                needsLiveBalance = true
            ),
            AssetRow("error-hidden", enabled = HIDDEN, expectedEnabled = HIDDEN, free = "-1", needsLiveBalance = false)
        )
    }
}
