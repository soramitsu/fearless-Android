package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

class RollbackOnlySqlPreviewTest {
    @Test
    fun `success restores before releasing and returns the result`() {
        val statements = mutableListOf<String>()
        val database = database(statements)
        val result = withRollbackOnlySqlPreview(database) {
            statements += "ACTION"
            42
        }
        assertEquals(42, result)
        assertEquals(listOf(SAVE, "ACTION", ROLLBACK, RELEASE), statements)
    }

    @Test
    fun `action failure is rethrown after restoring the savepoint`() {
        val statements = mutableListOf<String>()
        val original = IllegalStateException("synthetic action failure")
        val actual = assertThrows(IllegalStateException::class.java) {
            withRollbackOnlySqlPreview(database(statements)) { throw original }
        }
        assertSame(original, actual)
        assertEquals(listOf(SAVE, ROLLBACK, RELEASE), statements)
    }

    @Test
    fun `failed rollback never releases possibly changed data`() {
        val statements = mutableListOf<String>()
        val cleanup = IllegalStateException("synthetic rollback failure")
        val database = database(statements, rollbackFailure = cleanup)
        val actual = assertThrows(IllegalStateException::class.java) {
            withRollbackOnlySqlPreview(database) { 42 }
        }
        assertSame(cleanup, actual)
        assertEquals(listOf(SAVE, ROLLBACK), statements)
    }

    @Test
    fun `original failure retains cleanup failure without releasing`() {
        val statements = mutableListOf<String>()
        val original = IllegalArgumentException("synthetic action failure")
        val cleanup = IllegalStateException("synthetic rollback failure")
        val database = database(statements, rollbackFailure = cleanup)
        val actual = assertThrows(IllegalArgumentException::class.java) {
            withRollbackOnlySqlPreview(database) { throw original }
        }
        assertSame(original, actual)
        assertEquals(listOf(cleanup), actual.suppressed.toList())
        assertEquals(listOf(SAVE, ROLLBACK), statements)
    }

    @Test
    fun `outside a transaction no SQL or action executes`() {
        val statements = mutableListOf<String>()
        val database = database(statements, transactionOpen = false)
        assertThrows(IllegalStateException::class.java) {
            withRollbackOnlySqlPreview(database) { error("must not execute") }
        }
        assertTrue(statements.isEmpty())
    }

    @Test
    fun `failed savepoint creation does not run action or cleanup`() {
        val statements = mutableListOf<String>()
        val original = IllegalStateException("synthetic savepoint failure")
        val database = database(statements, saveFailure = original)
        val actual = assertThrows(IllegalStateException::class.java) {
            withRollbackOnlySqlPreview(database) { error("must not execute") }
        }
        assertSame(original, actual)
        assertEquals(listOf(SAVE), statements)
    }

    @Test
    fun `release failure fails the outer operation after rollback`() {
        val statements = mutableListOf<String>()
        val original = IllegalStateException("synthetic release failure")
        val database = database(statements, releaseFailure = original)
        val actual = assertThrows(IllegalStateException::class.java) {
            withRollbackOnlySqlPreview(database) { 42 }
        }
        assertSame(original, actual)
        assertEquals(listOf(SAVE, ROLLBACK, RELEASE), statements)
    }

    private fun database(
        statements: MutableList<String>,
        transactionOpen: Boolean = true,
        saveFailure: Throwable? = null,
        rollbackFailure: Throwable? = null,
        releaseFailure: Throwable? = null
    ): SupportSQLiteDatabase = mock {
        on { inTransaction() } doAnswer { transactionOpen }
        on { execSQL(any()) } doAnswer { invocation ->
            val sql = invocation.arguments[0] as String
            statements += sql
            val failure = when (sql) {
                SAVE -> saveFailure
                ROLLBACK -> rollbackFailure
                RELEASE -> releaseFailure
                else -> error("Unexpected statement")
            }
            if (failure != null) throw failure
            null
        }
    }

    private companion object {
        const val SAVE = "SAVEPOINT fearless_upgrade_preview"
        const val ROLLBACK = ";ROLLBACK TO SAVEPOINT fearless_upgrade_preview"
        const val RELEASE = "RELEASE SAVEPOINT fearless_upgrade_preview"
    }
}
