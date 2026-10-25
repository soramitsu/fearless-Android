package jp.co.soramitsu.app.root.domain.keyloss

import android.content.Context
import android.content.ContentValues
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletPublicSnapshotReaderInstrumentedTest {
    @Test fun cachedAmountsUseKnownPrecisionAndOversizedRecordsAreDisclosed() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val file = File(app.cacheDir, "keyloss-bounds.db")
        file.delete()
        val context = object : ContextWrapper(app) { override fun getDatabasePath(name: String): File = file }
        try {
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
                database.execSQL("CREATE TABLE meta_accounts (id INTEGER, name TEXT, ethereumAddress BLOB)")
                database.execSQL("INSERT INTO meta_accounts VALUES(1, 'Public fixture', ?)", arrayOf(ByteArray(20) { 1 }))
                database.execSQL("INSERT INTO meta_accounts VALUES(2, ?, ?)", arrayOf("x".repeat(5000), ByteArray(129)))
                database.execSQL("CREATE TABLE assets(metaId INTEGER, chainId TEXT, id TEXT, freeInPlanks TEXT, reservedInPlanks TEXT)")
                database.execSQL("CREATE TABLE chain_assets(chainId TEXT, id TEXT, symbol TEXT, precision INTEGER)")
                database.execSQL("INSERT INTO chain_assets VALUES('polkadot', '0', 'DOT', 10)")
                database.execSQL("INSERT INTO assets VALUES(1, 'polkadot', '0', '10000000000', '5000000000')")
            }
            val snapshot = WalletPublicSnapshotReader(context).read()
            assertEquals(2, snapshot.wallets.size)
            assertEquals("1.5 DOT", snapshot.wallets.first().balances.single().amount)
            assertTrue(snapshot.incomplete)
            assertEquals("", snapshot.wallets.last().name)
            assertTrue(snapshot.wallets.last().identities.isEmpty())
        } finally { file.delete() }
    }

    @Test fun historicalPublicSnapshotsOpenReadOnlyWithoutMigrationOrByteChanges() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        for (fixture in listOf("shipped-3.7.4-v71.db", "shipped-2.0.0-v30.db")) {
            val file = File(app.cacheDir, "keyloss-$fixture")
            try {
                InstrumentationRegistry.getInstrumentation().context.assets.open("keyloss/$fixture").use { input ->
                    file.outputStream().use(input::copyTo)
                }
                // Frozen release assets contain exact schema DDL, not wallet rows.
                // Add public-only data to this disposable copy before the read-only check.
                android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, 0).use { database ->
                    val columns = database.rawQuery("PRAGMA table_info(meta_accounts)", null).use { cursor ->
                        buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                    }
                    val row = ContentValues().apply {
                        put("id", 1L)
                        put("name", "Historical public wallet")
                        put("substratePublicKey", ByteArray(32) { 1 })
                        put("substrateAccountId", ByteArray(32) { 1 })
                        put("substrateCryptoType", "SR25519")
                        put("isSelected", 1)
                        put("position", 0)
                        for (field in listOf("isBackedUp", "initialized")) if (field in columns) put(field, 0)
                    }
                    database.insertOrThrow("meta_accounts", null, row)
                }
                val before = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                val context = object : ContextWrapper(app) { override fun getDatabasePath(name: String): File = file }
                val snapshot = WalletPublicSnapshotReader(context).read()
                assertTrue("$fixture wallet list", snapshot.wallets.isNotEmpty())
                assertTrue("$fixture identities", snapshot.wallets.any { it.identities.isNotEmpty() })
                assertArrayEquals(before, MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
            } finally { file.delete() }
        }
    }
}
