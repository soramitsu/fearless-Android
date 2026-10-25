package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.Mockito.*

class FiatCurrencyIdentityMigrationTest {
    @Test fun migrationAddsOnlyNullableIdentityWithinExistingTransaction() {
        val db = mock(SupportSQLiteDatabase::class.java)
        `when`(db.inTransaction()).thenReturn(true)
        FiatCurrencyIdentityMigration.migrate(db)
        val sql = argumentCaptor<String>()
        verify(db).inTransaction(); verify(db).execSQL(sql.capture()); verifyNoMoreInteractions(db)
        assertEquals("ALTER TABLE token_price ADD COLUMN fiatCurrencyId TEXT", sql.firstValue)
        System.getProperty("fiat.migration.sql")?.let { File(it).writeText(sql.firstValue) }
        assertEquals(83, FiatCurrencyIdentityMigration.startVersion); assertEquals(84, FiatCurrencyIdentityMigration.endVersion)
    }

    @Test fun missingTransactionCannotWriteAnySchemaOrPrices() {
        val db = mock(SupportSQLiteDatabase::class.java)
        try { FiatCurrencyIdentityMigration.migrate(db); fail("Must require caller transaction") }
        catch (expected: IllegalStateException) { assertEquals("Fiat currency identity migration requires a transaction", expected.message) }
        verify(db).inTransaction(); verifyNoMoreInteractions(db)
    }

    @Test fun failedSchemaWritePropagatesOriginalFailureToRoom() {
        val db = mock(SupportSQLiteDatabase::class.java)
        val error = IllegalStateException("public-fixture-alter-failure")
        `when`(db.inTransaction()).thenReturn(true)
        doThrow(error).`when`(db).execSQL(FiatCurrencyIdentityMigration.ADD_CURRENCY_ID)
        try { FiatCurrencyIdentityMigration.migrate(db); fail("Must abort migration") }
        catch (actual: IllegalStateException) { assertSame(error, actual) }
        verify(db).inTransaction(); verify(db).execSQL(FiatCurrencyIdentityMigration.ADD_CURRENCY_ID); verifyNoMoreInteractions(db)
    }
}
