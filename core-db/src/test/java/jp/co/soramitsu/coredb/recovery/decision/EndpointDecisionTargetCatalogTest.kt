package jp.co.soramitsu.coredb.recovery.decision

import android.database.Cursor
import android.os.CancellationSignal
import android.os.OperationCanceledException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.same
import org.mockito.kotlin.argumentCaptor
import org.mockito.Mockito.*

class EndpointDecisionTargetCatalogTest {
    @Test fun invalidPagingRejectsBeforeAnyDatabaseOrSignalInteraction() {
        val db = mock(SupportSQLiteDatabase::class.java)
        val signal = mock(CancellationSignal::class.java)
        val catalog = EndpointDecisionTargetCatalog(db)
        for (size in listOf(Int.MIN_VALUE, 0, 51, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { catalog.readPage(signal, pageSize = size) }
        }
        assertThrows(IllegalArgumentException::class.java) { catalog.readPage(signal, 1, 2) }
        verifyNoInteractions(db, signal)
    }

    @Test fun aNestedReadNeverEndsOrMarksItsCallersTransaction() {
        val db = mock(SupportSQLiteDatabase::class.java)
        val signal = mock(CancellationSignal::class.java)
        `when`(db.inTransaction()).thenReturn(true)
        assertThrows(IllegalStateException::class.java) { EndpointDecisionTargetCatalog(db).readPage(signal) }
        verify(db, never()).beginTransactionNonExclusive()
        verify(db, never()).endTransaction()
        verify(db, never()).setTransactionSuccessful()
    }

    @Test fun emptyCatalogReturnsNoInventedTargetsOrCursorsWithoutStartingATransaction() {
        val db = mock(SupportSQLiteDatabase::class.java)
        val signal = mock(CancellationSignal::class.java)
        val cursor = mock(Cursor::class.java)
        `when`(cursor.columnCount).thenReturn(12)
        val encoding = encodingCursor()
        `when`(db.query(any<SupportSQLiteQuery>(), same(signal))).thenReturn(encoding, cursor)
        val page = EndpointDecisionTargetCatalog(db).readPage(signal)
        assertTrue(page.options.isEmpty())
        assertNull(page.previousBeforeRowId); assertNull(page.nextAfterRowId)
        verify(cursor).close(); verify(encoding).close()
        verify(db, never()).beginTransactionNonExclusive()
        verify(db, never()).endTransaction()
        verify(db, never()).setTransactionSuccessful()
    }

    @Test fun aQueryFailureClosesEncodingAndNeverBeginsOrEndsATransaction() {
        val db = mock(SupportSQLiteDatabase::class.java)
        val signal = mock(CancellationSignal::class.java)
        val failure = UnsupportedOperationException("synthetic query failure")
        val encoding = encodingCursor()
        `when`(db.query(any<SupportSQLiteQuery>(), same(signal))).thenReturn(encoding).thenThrow(failure)
        assertSame(failure, assertThrows(UnsupportedOperationException::class.java) {
            EndpointDecisionTargetCatalog(db).readPage(signal)
        })
        verify(encoding).close()
        verify(db, never()).beginTransactionNonExclusive()
        verify(db, never()).endTransaction()
        verify(db, never()).setTransactionSuccessful()
    }

    @Test fun pageOwnsItsListAndAcceptsOrderedSignedBoundaries() {
        val first = EndpointDecisionTargetOption(Long.MIN_VALUE, "private label", null)
        val last = EndpointDecisionTargetOption(Long.MAX_VALUE, null, null)
        val source = mutableListOf(first, last)
        val page = EndpointDecisionTargetPage(source, first.rowId, last.rowId)
        source.clear()
        assertEquals(listOf(first, last), page.options)
        assertEquals(Long.MIN_VALUE, page.previousBeforeRowId)
        assertEquals(Long.MAX_VALUE, page.nextAfterRowId)
        assertFalse(page.toString().contains("private"))
        assertFalse(first.toString().contains("private"))
    }

    @Test fun invalidPageBoundariesDuplicatesOrUnboundedRowsReject() {
        val row = EndpointDecisionTargetOption(7, null, null)
        assertThrows(IllegalArgumentException::class.java) { EndpointDecisionTargetPage(emptyList(), 7, null) }
        assertThrows(IllegalArgumentException::class.java) { EndpointDecisionTargetPage(listOf(row), 6, null) }
        assertThrows(IllegalArgumentException::class.java) { EndpointDecisionTargetPage(listOf(row), null, 8) }
        assertThrows(IllegalArgumentException::class.java) { EndpointDecisionTargetPage(listOf(row, row), null, null) }
        val reversed = listOf(EndpointDecisionTargetOption(9, null, null), row)
        assertThrows(IllegalArgumentException::class.java) { EndpointDecisionTargetPage(reversed, null, null) }
        val oversized = (0L..50L).map { EndpointDecisionTargetOption(it, null, null) }
        assertThrows(IllegalArgumentException::class.java) { EndpointDecisionTargetPage(oversized, null, null) }
    }
    @Test fun preCancelledReadRejectsBeforeTransactionOrEncodingAdmission() {
        val db = mock(SupportSQLiteDatabase::class.java)
        val signal = mock(CancellationSignal::class.java)
        val failure = mock(OperationCanceledException::class.java)
        doThrow(failure).`when`(signal).throwIfCanceled()
        assertSame(failure, assertThrows(OperationCanceledException::class.java) {
            EndpointDecisionTargetCatalog(db).readPage(signal)
        })
        verifyNoInteractions(db)
    }

    @Test fun unsupportedEncodingRejectsAfterOnlyTheCancellableScalarQuery() {
        val db = mock(SupportSQLiteDatabase::class.java)
        val signal = mock(CancellationSignal::class.java)
        val encoding = encodingCursor("UTF-16le")
        `when`(db.query(any<SupportSQLiteQuery>(), same(signal))).thenReturn(encoding)
        val error = assertThrows(IllegalStateException::class.java) { EndpointDecisionTargetCatalog(db).readPage(signal) }
        assertEquals("Endpoint catalog requires UTF-8 database encoding", error.message)
        val query = argumentCaptor<SupportSQLiteQuery>()
        verify(db).query(query.capture(), same(signal))
        assertEquals("PRAGMA main.encoding", query.firstValue.sql)
        verify(encoding).close(); verify(db, never()).beginTransactionNonExclusive()
    }

    @Test fun oneSnapshotRowCapturesIdentityAndNavigationWithoutFollowupLookup() {
        val db = mock(SupportSQLiteDatabase::class.java)
        val signal = mock(CancellationSignal::class.java)
        val row = mock(Cursor::class.java)
        `when`(row.columnCount).thenReturn(12)
        `when`(row.moveToNext()).thenReturn(true, false)
        for (column in listOf(0, 4, 5, 6, 9, 10, 11)) `when`(row.getType(column)).thenReturn(Cursor.FIELD_TYPE_INTEGER)
        for ((column, text) in listOf(1 to "12".repeat(32), 2 to "Local name", 3 to "Substrate")) {
            `when`(row.getType(column)).thenReturn(Cursor.FIELD_TYPE_BLOB)
            `when`(row.getBlob(column)).thenReturn(text.toByteArray())
        }
        `when`(row.getLong(0)).thenReturn(7L)
        `when`(row.getLong(10)).thenReturn(1L)
        `when`(row.getType(7)).thenReturn(Cursor.FIELD_TYPE_STRING)
        `when`(row.getString(7)).thenReturn("null")
        `when`(row.isNull(8)).thenReturn(true)
        val encoding = encodingCursor()
        `when`(db.query(any<SupportSQLiteQuery>(), same(signal))).thenReturn(encoding, row)
        val page = EndpointDecisionTargetCatalog(db).readPage(signal)
        assertEquals("Local name", page.options.single().name)
        assertEquals("0x" + "12".repeat(32), page.options.single().target!!.expectedGenesis)
        assertEquals(7L, page.previousBeforeRowId); assertNull(page.nextAfterRowId)
        verify(db, times(2)).query(any<SupportSQLiteQuery>(), same(signal))
        verify(db, never()).beginTransactionNonExclusive(); verify(db, never()).endTransaction()
        verify(row).close()
    }

    private fun encodingCursor(value: String = "UTF-8"): Cursor = mock(Cursor::class.java).also {
        `when`(it.columnCount).thenReturn(1)
        `when`(it.moveToFirst()).thenReturn(true)
        `when`(it.getType(0)).thenReturn(Cursor.FIELD_TYPE_STRING)
        `when`(it.getString(0)).thenReturn(value)
    }

}
