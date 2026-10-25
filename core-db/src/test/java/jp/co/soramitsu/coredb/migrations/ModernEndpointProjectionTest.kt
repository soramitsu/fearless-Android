package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ModernEndpointProjectionTest {
    @Test
    fun `projection requires a transaction before even reading archive evidence`() {
        val database = mock<SupportSQLiteDatabase>()
        var inspected = false
        val projection = ModernEndpointProjection { inspected = true; true }
        assertThrows(IllegalStateException::class.java) { projection.retainOrdinaryRows(database) }
        org.junit.Assert.assertFalse(inspected)
        verify(database, never()).execSQL(any<String>())
    }

    @Test
    fun `absent archive cannot authorize operational deletion`() {
        val database = mock<SupportSQLiteDatabase> { on { inTransaction() } doReturn true }
        assertThrows(IllegalStateException::class.java) {
            ModernEndpointProjection { false }.retainOrdinaryRows(database)
        }
        verify(database, never()).execSQL(any<String>())
    }

    @Test
    fun `archive validation failure propagates unchanged before deletion`() {
        val database = mock<SupportSQLiteDatabase> { on { inTransaction() } doReturn true }
        val original = IllegalStateException("public archive mismatch")
        val caught = assertThrows(IllegalStateException::class.java) {
            ModernEndpointProjection { throw original }.retainOrdinaryRows(database)
        }
        assertSame(original, caught)
        verify(database, never()).execSQL(any<String>())
    }

    @Test
    fun `SQL failure remains owned by callers rollback transaction`() {
        val original = IllegalStateException("public SQL failure")
        val database = mock<SupportSQLiteDatabase> {
            on { inTransaction() } doReturn true
            on { execSQL(any<String>()) } doAnswer { throw original }
        }
        val caught = assertThrows(IllegalStateException::class.java) {
            ModernEndpointProjection { true }.retainOrdinaryRows(database)
        }
        assertSame(original, caught)
        verify(database, never()).setTransactionSuccessful()
        verify(database, never()).endTransaction()
    }
}
