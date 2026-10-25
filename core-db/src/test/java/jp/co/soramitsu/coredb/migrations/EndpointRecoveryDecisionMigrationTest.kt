package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class EndpointRecoveryDecisionMigrationTest {
    @Test fun outsideTransactionRejectsBeforeSourceQueriesOrDdl() {
        val db = mock<SupportSQLiteDatabase>()
        assertThrows(IllegalStateException::class.java) { EndpointRecoveryDecisionMigration.migrate(db) }
        verify(db).inTransaction()
        verifyNoMoreInteractions(db)
    }

    @Test fun namespaceCollisionRejectsBeforeMutationAndAlwaysClosesCursor() {
        val cursor = mock<Cursor> { on { moveToFirst() } doReturn true }
        val db = mock<SupportSQLiteDatabase> {
            on { inTransaction() } doReturn true
            on { query(any<String>()) } doReturn cursor
        }
        assertThrows(IllegalStateException::class.java) { EndpointRecoveryDecisionMigration.migrate(db) }
        verify(cursor).close()
        verify(db, never()).execSQL(any<String>())
    }

    @Test fun emptyNamespaceCreatesOnlyExactOfflineTableAndUniqueIndexWithoutVersionOrParentFk() {
        val cursors = mutableListOf<Cursor>()
        val db = mock<SupportSQLiteDatabase> {
            on { inTransaction() } doReturn true
            on { query(any<String>()) } doAnswer {
                mock<Cursor> { on { moveToFirst() } doReturn false }.also(cursors::add)
            }
        }
        EndpointRecoveryDecisionMigration.migrate(db)
        val statements = argumentCaptor<String>()
        verify(db, times(2)).execSQL(statements.capture())
        assertEquals(listOf(EndpointRecoveryDecisionMigration.CREATE, EndpointRecoveryDecisionMigration.CREATE_INDEX), statements.allValues)
        assertTrue(statements.allValues.none { it.contains("FOREIGN KEY") || it.contains("user_version") })
        assertEquals(2, cursors.size); cursors.forEach { verify(it).close() }
        assertEquals(82, EndpointRecoveryDecisionMigration.startVersion)
        assertEquals(83, EndpointRecoveryDecisionMigration.endVersion)
    }
}
