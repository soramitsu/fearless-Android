package jp.co.soramitsu.app.root.domain.keyloss

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The read-only public snapshot against real SQLite files laid out like the wallet's database. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WalletPublicSnapshotDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file get() = context.getDatabasePath("app.db")

    @Before
    fun clean() {
        file.parentFile?.mkdirs()
        SQLiteDatabase.deleteDatabase(file)
    }

    @After
    fun tearDown() {
        SQLiteDatabase.deleteDatabase(file)
    }

    @Test
    fun walletsShowPublicIdentitiesAndPricedBalances() {
        database {
            execSQL("CREATE TABLE meta_accounts (id INTEGER PRIMARY KEY, name TEXT, substrateAccountId BLOB, ethereumAddress BLOB, tonPublicKey BLOB, secret TEXT)")
            execSQL("CREATE TABLE assets (metaId INTEGER, chainId TEXT, id TEXT, freeInPlanks TEXT, reservedInPlanks TEXT)")
            execSQL("CREATE TABLE chain_assets (chainId TEXT, id TEXT, symbol TEXT, precision INTEGER)")
            execSQL("INSERT INTO meta_accounts VALUES (7, 'Main', X'0102', X'00112233445566778899aabbccddeeff00112233', NULL, 'never shown')")
            execSQL("INSERT INTO assets VALUES (7, 'polkadot', 'dot', '15000000000', '5000000000')")
            execSQL("INSERT INTO assets VALUES (7, 'kusama', 'ksm', '42', '0')")
            execSQL("INSERT INTO assets VALUES (7, 'westend', 'wnd', '-1', '3')")
            execSQL("INSERT INTO chain_assets VALUES ('polkadot', 'dot', 'DOT', 10)")
        }

        val snapshot = WalletPublicSnapshotReader(context).read()

        assertFalse(snapshot.incomplete)
        val wallet = snapshot.wallets.single()
        assertEquals("Main", wallet.name)
        assertEquals(
            listOf("substrateAccountId" to "0102", "ethereumAddress" to "00112233445566778899aabbccddeeff00112233"),
            wallet.identities
        )
        assertFalse("Secrets are never projected", wallet.identities.any { it.second == "never shown" })
        assertEquals(
            listOf(
                WalletPublicSnapshot.Balance("polkadot", "dot", "2 DOT"),
                WalletPublicSnapshot.Balance("kusama", "ksm", "42"),
                WalletPublicSnapshot.Balance("westend", "wnd", "?")
            ),
            wallet.balances
        )
    }

    @Test
    fun balancesWithoutAssetMetadataShowRawPlanks() {
        database {
            execSQL("CREATE TABLE meta_accounts (id INTEGER, name TEXT, substrateAccountId BLOB)")
            execSQL("CREATE TABLE assets (metaId INTEGER, chainId TEXT, id TEXT, freeInPlanks TEXT, reservedInPlanks TEXT)")
            execSQL("INSERT INTO meta_accounts VALUES (1, 'Only', X'aa')")
            execSQL("INSERT INTO assets VALUES (1, 'polkadot', 'dot', '10', '5')")
        }
        val wallet = WalletPublicSnapshotReader(context).read().wallets.single()
        assertEquals(listOf(WalletPublicSnapshot.Balance("polkadot", "dot", "15")), wallet.balances)
    }

    @Test
    fun legacyTablesAreListedAndPartialRowsAreDisclosed() {
        database {
            execSQL("CREATE TABLE users (address TEXT, username TEXT, publicKey TEXT, cryptoType INTEGER, position INTEGER)")
            execSQL("CREATE TABLE legacy_users_recovery (address TEXT, username TEXT, publicKey TEXT, cryptoType INTEGER, position INTEGER, recoveryState TEXT)")
            execSQL("INSERT INTO users VALUES ('5Legacy', 'Old wallet', '0xpub', 0, 0)")
            execSQL("INSERT INTO legacy_users_recovery VALUES ('5Recovered', 'Recovered', NULL, 0, 1, 'set_aside')")
        }

        val snapshot = WalletPublicSnapshotReader(context).read()

        assertEquals(listOf("Old wallet", "Recovered"), snapshot.wallets.map { it.name })
        assertEquals(listOf("address" to "5Legacy", "publicKey" to "0xpub"), snapshot.wallets.first().identities)
        assertTrue("A legacy row without its public key is incomplete", snapshot.incomplete)
    }

    @Test
    fun unknownLayoutsAndOversizedValuesAreFlaggedIncomplete() {
        database {
            execSQL("CREATE TABLE meta_accounts (id INTEGER, name TEXT, address TEXT)")
            execSQL("CREATE TABLE users (address TEXT, username TEXT, extra TEXT)")
            execSQL("CREATE TABLE legacy_users_recovery (unrelated TEXT)")
            execSQL("INSERT INTO meta_accounts VALUES (1, 'Big', ?)", arrayOf<Any>("x".repeat(3000)))
            execSQL("INSERT INTO users VALUES ('5Unknown', 'Unknown layout', 'x')")
        }

        val snapshot = WalletPublicSnapshotReader(context).read()

        assertTrue(snapshot.incomplete)
        assertEquals(listOf("Big", "Unknown layout"), snapshot.wallets.map { it.name })
        assertTrue("An oversized value is not shown", snapshot.wallets.first().identities.isEmpty())
    }

    @Test
    fun aDatabaseWithoutWalletTablesIsEmptyAndIncomplete() {
        database { execSQL("CREATE TABLE unrelated (id INTEGER)") }
        assertEquals(WalletPublicSnapshot(emptyList(), true), WalletPublicSnapshotReader(context).read())
    }

    @Test
    fun walletsWithoutAnIdAreIncompleteAndBrokenBalancesDoNotHideThem() {
        database {
            execSQL("CREATE TABLE meta_accounts (name TEXT, substrateAccountId BLOB)")
            execSQL("INSERT INTO meta_accounts VALUES ('No id', X'01')")
        }
        val withoutId = WalletPublicSnapshotReader(context).read()
        assertTrue(withoutId.incomplete)
        assertEquals("No id", withoutId.wallets.single().name)

        SQLiteDatabase.deleteDatabase(file)
        database {
            execSQL("CREATE TABLE meta_accounts (id INTEGER, name TEXT, substrateAccountId BLOB)")
            execSQL("CREATE TABLE assets (metaId INTEGER, chainId TEXT)")
            execSQL("INSERT INTO meta_accounts VALUES (2, 'Broken assets', X'02')")
        }
        val brokenAssets = WalletPublicSnapshotReader(context).read()
        assertTrue(brokenAssets.incomplete)
        assertEquals(emptyList<WalletPublicSnapshot.Balance>(), brokenAssets.wallets.single().balances)
    }

    private fun database(block: SQLiteDatabase.() -> Unit) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use(block)
    }
}
