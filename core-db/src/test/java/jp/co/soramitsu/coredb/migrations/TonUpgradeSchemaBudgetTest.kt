package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class TonUpgradeSchemaBudgetTest {
    @Test
    fun `every supported schema combination fits the bounded object scan before SQL is read`() {
        val readFailure = IllegalStateException("Fixture stops at the first bounded schema query")
        val database = mock<SupportSQLiteDatabase> {
            on { query(any<String>()) } doAnswer { throw readFailure }
        }
        val failure = assertThrows(TonUpgradeSqlIntegrityException::class.java) {
            TonUpgradeSqlPreflight.requireSafeToMutate(database)
        }
        assertSame(readFailure, failure.cause)
        verify(database).query(any<String>())
        verify(database, never()).execSQL(any<String>())
        verify(database, never()).execSQL(any<String>(), any<Array<Any?>>())
    }
}
