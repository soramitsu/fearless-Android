package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exported Room schemas and disposable synthetic databases only; no default wallet files. */
class FiatCurrencyIdentityMigrationInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory()
    )
    @After fun cleanup() { names.forEach(context::deleteDatabase) }

    @Test fun populated83To84RetainsEveryLegacyQuoteByteAndProtectedTableAcrossReopen() {
        val name = name("83")
        val before = helper.createDatabase(name, 83).use { db -> seed(db); image(db) }
        helper.runMigrationsAndValidate(name, 84, true, FiatCurrencyIdentityMigration).use { db ->
            assertMigrated(db, before)
            assertEquals(IDENTITY_84, scalar(db, "SELECT identity_hash FROM room_master_table WHERE id=42"))
        }
        val preferences = preferences()
        val originalPreferences = preferences.snapshot()
        repeat(2) {
            val room = open(name, preferences)
            try {
                assertMigrated(room.openHelper.writableDatabase, before)
                assertEquals(originalPreferences, preferences.snapshot())
            } finally {
                room.close()
            }
        }
    }

    @Test fun production82To84AddsNullableCurrencyWithoutRelabellingOrChangingOwnedRows() {
        val name = name("82")
        val before = helper.createDatabase(name, 82).use { db -> seed(db); image(db) }
        val preferences = preferences()
        val originalPreferences = preferences.snapshot()
        repeat(2) {
            val room = open(name, preferences)
            try {
                val db = room.openHelper.writableDatabase
                assertMigrated(db, before)
                assertEquals(IDENTITY_84, scalar(db, "SELECT identity_hash FROM room_master_table WHERE id=42"))
                assertEquals("0", scalar(db, "SELECT COUNT(*) FROM endpoint_recovery_decisions"))
                assertEquals(originalPreferences, preferences.snapshot())
            } finally {
                room.close()
            }
        }
        // Validate the production builder's result against the exported Room84 schema too.
        helper.runMigrationsAndValidate(name, 84, true).use { assertMigrated(it, before) }
    }

    @Test fun production76To84RetainsLegacyQuoteBytesWithoutInventingSelectedCurrency() {
        val name = name("76")
        // Actual wallet/secret preservation is independently covered by the retained
        // version76ValidSr25519EthereumAndTonSecretsSurviveTwoProductionLaunches case.
        val before = helper.createDatabase(name, 76).use { db -> seedQuotes(db); image(db) }
        val preferences = preferences()
        val originalPreferences = preferences.snapshot()
        repeat(2) {
            val room = open(name, preferences)
            try {
                val db = room.openHelper.writableDatabase
                assertMigrated(db, before)
                assertEquals(IDENTITY_84, scalar(db, "SELECT identity_hash FROM room_master_table WHERE id=42"))
                assertEquals(originalPreferences, preferences.snapshot())
            } finally {
                room.close()
            }
        }
        helper.runMigrationsAndValidate(name, 84, true).use { assertMigrated(it, before) }
    }

    @Test fun nativeFailureAfterAlterRollsBackColumnVersionAndRowsThenSameDatabaseRetries() {
        val name = name("rollback")
        val before = helper.createDatabase(name, 83).use { db ->
            seed(db)
            val before = image(db)
            val schemaBefore = rawRows(db, "sqlite_master")
            val expected = SQLiteException("PUBLIC_FIXTURE_AFTER_CURRENCY_ALTER")
            val actual = assertThrows(SQLiteException::class.java) {
                db.beginTransaction()
                try {
                    FiatCurrencyIdentityMigration.migrate(db)
                    assertTrue(columns(db, "token_price").contains("fiatCurrencyId"))
                    db.version = 84
                    throw expected
                } finally { db.endTransaction() }
            }
            assertSame(expected, actual)
            assertFalse(db.inTransaction())
            assertEquals(83, db.version)
            assertFalse(columns(db, "token_price").contains("fiatCurrencyId"))
            assertEquals(schemaBefore, rawRows(db, "sqlite_master"))
            assertEquals(before, image(db))
            healthy(db)
            before
        }
        helper.runMigrationsAndValidate(name, 84, true, FiatCurrencyIdentityMigration).use { db ->
            assertMigrated(db, before)
            assertEquals(IDENTITY_84, scalar(db, "SELECT identity_hash FROM room_master_table WHERE id=42"))
        }
        val preferences = preferences()
        val originalPreferences = preferences.snapshot()
        repeat(2) {
            val room = open(name, preferences)
            try {
                val db = room.openHelper.writableDatabase
                assertMigrated(db, before)
                assertEquals(IDENTITY_84, scalar(db, "SELECT identity_hash FROM room_master_table WHERE id=42"))
                assertEquals(originalPreferences, preferences.snapshot())
            } finally {
                room.close()
            }
        }
    }

    private data class Image(
        val prices: List<List<String?>>,
        val protectedRows: Map<String, List<List<String?>>>
    )

    private fun name(suffix: String) = "fiat-currency-$suffix-${UUID.randomUUID()}".also(names::add)
    private fun quote(value: String) = "`" + value.replace("`", "``") + "`"
    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(${quote(table)})").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
        }

    private fun rawRows(db: SupportSQLiteDatabase, table: String, fields: List<String> = columns(db, table)): List<List<String?>> {
        val projection = (listOf("rowid") + fields).flatMap { field ->
            val column = quote(field)
            listOf("typeof($column)", "hex(CAST($column AS BLOB))")
        }.joinToString(",")
        return db.query("SELECT $projection FROM ${quote(table)} ORDER BY rowid").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { index ->
                if (cursor.isNull(index)) null else cursor.getString(index)
            }) }
        }
    }

    private fun protectedTables(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' " +
            "AND name NOT IN ('token_price','room_master_table') ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun image(db: SupportSQLiteDatabase) = Image(
        rawRows(db, "token_price", LEGACY_PRICE_COLUMNS),
        protectedTables(db).associateWith { rawRows(db, it) }
    )

    private fun scalar(db: SupportSQLiteDatabase, sql: String): String = db.query(sql).use {
        assertTrue(it.moveToFirst()); requireNotNull(it.getString(0))
    }

    private fun healthy(db: SupportSQLiteDatabase) {
        assertEquals("ok", scalar(db, "PRAGMA quick_check"))
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    private fun assertMigrated(db: SupportSQLiteDatabase, before: Image) {
        assertEquals(84, db.version)
        assertEquals(before.prices, rawRows(db, "token_price", LEGACY_PRICE_COLUMNS))
        before.protectedRows.forEach { (table, rows) -> assertEquals(table, rows, rawRows(db, table)) }
        db.query("PRAGMA table_info(token_price)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) if (cursor.getString(1) == "fiatCurrencyId") {
                assertFalse(found); found = true
                assertEquals("TEXT", cursor.getString(2)); assertEquals(0, cursor.getInt(3))
                assertTrue(cursor.isNull(4)); assertEquals(0, cursor.getInt(5))
            }
            assertTrue(found)
        }
        assertEquals("0", scalar(db, "SELECT COUNT(*) FROM token_price WHERE fiatCurrencyId IS NOT NULL"))
        assertEquals(before.prices.size.toString(), scalar(db, "SELECT COUNT(*) FROM token_price WHERE typeof(fiatCurrencyId)='null'"))
        healthy(db)
    }

    private fun preferences() = HashMapEncryptedPreferences().apply {
        putEncryptedString("fixture-currency-selection", "eur")
        putEncryptedString("fixture-protected-marker", "PUBLIC_ONLY_雪")
    }

    private fun HashMapEncryptedPreferences.snapshot(): Map<String, String?> =
        keysWithPrefixes(setOf(""), 8_192, 1_024, 1_048_576, true)
            .associateWith(::getDecryptedString)

    private fun open(name: String, preferences: HashMapEncryptedPreferences) = AppDatabase.create(
        context, name, SecretStoreV1Impl(preferences), SecretStoreV2(preferences), preferences,
        SubstrateSecretStore(preferences), EthereumSecretStore(preferences)
    )

    private fun seedQuotes(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO token_price(priceId,fiatSymbol,fiatRate,recentRateChange) VALUES" +
            "('healthy','$','12.3400','-0.5'),('missing','€',NULL,NULL)," +
            "('malformed','USD','not-a-number','NaN'),('blob','¥',X'00FF80',X'FF00')," +
            "('exponent','USD','1e999999999','-1e999999999'),('zero','$','-0.000','0')," +
            "('empty','', '', ''),('invalid-text','USD',CAST(X'4EFF00' AS TEXT),'7')")
    }

    private fun seed(db: SupportSQLiteDatabase) {
        seedQuotes(db)
        db.execSQL("INSERT INTO chains(id,name,icon,prefix,isEthereumBased,isTestNet,hasCrowdloans," +
            "supportStakingPool,isEthereumChain,isChainlinkProvider,supportNft,isUsesAppId,ecosystem) " +
            "VALUES('fixture','Public chain','icon',42,0,0,0,0,0,0,0,0,'Substrate')")
        db.execSQL("INSERT INTO meta_accounts(id,name,isSelected,position,isBackedUp,initialized,substratePublicKey,substrateAccountId) " +
            "VALUES(9001,'Public wallet 雪',1,3,1,1,X'00FF80',X'00FF80')")
        db.execSQL("INSERT INTO chain_accounts(metaId,chainId,publicKey,accountId,cryptoType,name,initialized) " +
            "VALUES(9001,'fixture',X'00FF80',X'00FF80','SR25519','Public override',1)")
        db.execSQL("INSERT INTO assets(id,chainId,accountId,metaId,tokenPriceId,freeInPlanks,sortIndex,enabled,markedNotNeed,chainAccountName) " +
            "VALUES('native','fixture',X'00FF80',9001,'healthy','00042',-7,0,1,'Public asset')")
        db.execSQL("INSERT INTO address_book(address,name,chainId,created) VALUES('public-address','Public contact','fixture',123)")
        db.execSQL("INSERT INTO chain_nodes(chainId,url,name,isActive,isDefault) VALUES('fixture','https://public.invalid','Public node',1,0)")
        db.execSQL("INSERT INTO custom_endpoint_records(id,revision,chainId,url,name,attached,deleted,originalSnapshotRowId) " +
            "VALUES(42,7,'fixture','https://public.invalid','Public endpoint',1,0,-7)")
        if (protectedTables(db).contains("endpoint_recovery_decisions")) db.execSQL(
            "INSERT INTO endpoint_recovery_decisions(id,revision,sourceKind,sourceRowId,sourceRevision,sourceFingerprint," +
                "candidateName,candidateUrl,targetChainId,targetIdentityFingerprint,targetFamily,state) " +
                "VALUES(5,3,'public-fixture',-7,2,'source','Public candidate','https://candidate.invalid','fixture','target','Substrate','pending')")
        db.execSQL("INSERT INTO ton_connection(metaId,clientId,name,icon,url,source) " +
            "VALUES(9001,'public-client','Public dApp','icon','https://dapp.invalid','fixture')")
        healthy(db)
    }

    private companion object {
        val LEGACY_PRICE_COLUMNS = listOf("priceId", "fiatSymbol", "fiatRate", "recentRateChange")
        const val IDENTITY_84 = "70930b8dac42f63e6c74a748eae0872b"
    }
}
