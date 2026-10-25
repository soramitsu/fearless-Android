package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class HistoricalNodeUpgradePreflightTest {
    @Test
    fun `V2 without an archive performs no preview or transaction changes`() {
        val database = payloadDatabase(emptyList())
        HistoricalNodeUpgradePreflight.requireSafeBeforeV2SecretWrite(database)
        verify(database, never()).inTransaction()
        verify(database, never()).execSQL(any())
    }

    @Test
    fun `payload accounting shares the budget and includes padded null cells`() {
        val database = payloadDatabase(listOf(listOf(null, 2L), listOf(8L, 4L)))
        assertEquals(
            29L,
            requireBoundedHistoricalPayload(
                database, "example", listOf("key", "name"), 2, 10L,
                TonUpgradePayloadLimits(10, 20, 40), minimumCellBytes = mapOf("key" to 5L)
            )
        )
        verify(database).query(
            "SELECT length(CAST(`key` AS BLOB)) AS payload0, length(CAST(`name` AS BLOB)) AS payload1 " +
                "FROM `example` LIMIT 3"
        )
    }

    @Test
    fun `cell row and cumulative limits each fail before accepting oversized data`() {
        listOf(
            TonUpgradePayloadLimits(5, 20, 40) to listOf(listOf(6L, 1L)),
            TonUpgradePayloadLimits(10, 10, 40) to listOf(listOf(6L, 5L)),
            TonUpgradePayloadLimits(10, 20, 12) to listOf(listOf(6L, 1L), listOf(6L, 1L))
        ).forEach { (limits, rows) ->
            assertThrows(TonUpgradeSqlIntegrityException::class.java) {
                requireBoundedHistoricalPayload(payloadDatabase(rows), "example", listOf("a", "b"), 2, 0, limits)
            }
        }
    }

    @Test
    fun `padding cannot hide longer existing cells or exceed the row budget`() {
        listOf(
            payloadDatabase(listOf(listOf(80L))) to TonUpgradePayloadLimits(65, 100, 100),
            payloadDatabase(listOf(listOf(null))) to TonUpgradePayloadLimits(100, 64, 100)
        ).forEach { (database, limits) ->
            assertThrows(TonUpgradeSqlIntegrityException::class.java) {
                requireBoundedHistoricalPayload(
                    database, "example", listOf("key"), 1, 0, limits,
                    minimumCellBytes = mapOf("key" to 65L)
                )
            }
        }
    }

    @Test
    fun `row probe stops at limit plus one without reading excess payload`() {
        val cursor = lengthCursor(listOf(listOf(1L), listOf(2L)))
        val database: SupportSQLiteDatabase = mock { on { query(any<String>()) } doAnswer { cursor } }
        assertThrows(TonUpgradeSqlIntegrityException::class.java) {
            requireBoundedHistoricalPayload(database, "example", listOf("a"), 1, 0, LIMITS)
        }
        verify(cursor).getLong(0)
        verify(cursor).close()
    }

    @Test
    fun `negative and noninteger metadata fail without materializing a value`() {
        listOf(-1L, "12").forEach { value ->
            assertThrows(TonUpgradeSqlIntegrityException::class.java) {
                requireBoundedHistoricalPayload(payloadDatabase(listOf(listOf(value))), "example", listOf("a"), 1, 0, LIMITS)
            }
        }
    }

    @Test
    fun `payload sum overflow fails closed`() {
        assertThrows(TonUpgradeSqlIntegrityException::class.java) {
            requireBoundedHistoricalPayload(
                payloadDatabase(listOf(listOf(Long.MAX_VALUE - 2, 4L))),
                "example", listOf("a", "b"), 1, 0,
                TonUpgradePayloadLimits(Long.MAX_VALUE - 1, Long.MAX_VALUE - 1, Long.MAX_VALUE - 1)
            )
        }
    }

    @Test
    fun `empty table retains the previous staging total`() {
        assertEquals(12L, requireBoundedHistoricalPayload(payloadDatabase(emptyList()), "example", listOf("a"), 1, 12, LIMITS))
    }

    @Test
    fun `empty modern source still requires a transaction before archive admission preview`() {
        val database = nodeDatabase(retained = false)
        val failure = assertThrows(IllegalStateException::class.java) {
            HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(database)
        }
        assertTrue(failure.message.orEmpty().contains("transaction"))
        verify(database, never()).execSQL(any())
    }

    @Test
    fun `invalid node flags are rejected even when no retained node matches`() {
        val database = nodeDatabase(retained = false, invalidFlags = true)
        assertThrows(IllegalStateException::class.java) {
            HistoricalNodeUpgradePreflight.hasRetainedNodes(database)
        }
        verify(database, never()).execSQL(any())
    }

    @Test
    fun `retained nodes require a transaction before any staging query`() {
        val database = nodeDatabase(retained = true)
        val failure = assertThrows(IllegalStateException::class.java) {
            HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(database)
        }
        assertTrue(failure.message.orEmpty().contains("transaction"))
        verify(database, never()).query("PRAGMA foreign_keys")
        verify(database, never()).execSQL(any())
    }

    @Test
    fun `foreign keys enabled fail before any preview mutation`() {
        val database = nodeDatabase(retained = true, transactionOpen = true)
        val failure = assertThrows(IllegalStateException::class.java) {
            HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(database)
        }
        assertTrue(failure.message.orEmpty().contains("foreign keys disabled"))
        verify(database, never()).execSQL(any())
    }

    @Test
    fun `retained node and parent accounting shares a bounded staging total`() {
        val database: SupportSQLiteDatabase = mock {
            on { query(any<String>()) } doAnswer { invocation ->
                val sql = invocation.arguments[0] as String
                when {
                    sql.startsWith("SELECT length") && sql.contains("FROM `chain_nodes`") -> lengthCursor(listOf(List(5) { 2L }))
                    sql.startsWith("SELECT length") && sql.contains("FROM `chains`") -> lengthCursor(listOf(List(16) { 3L }))
                    sql.startsWith("SELECT 1") -> lengthCursor(emptyList())
                    else -> error("Unexpected query: $sql")
                }
            }
        }
        assertEquals(58L, HistoricalNodeUpgradePreflight.requireBoundedRetentionPayloads(database, LIMITS))
        verify(database, never()).execSQL(any())
    }

    @Test
    fun `missing archive cannot enter the legacy edge or mutate SQL`() {
        val database = payloadDatabase(emptyList())
        val failure = assertThrows(IllegalStateException::class.java) {
            HistoricalNodeUpgradePreflight.migrateLegacyNodes(database)
        }
        assertTrue(failure.message.orEmpty().contains("source is missing"))
        verify(database, never()).execSQL(any())
        verify(database, never()).inTransaction()
    }

    @Test
    fun `only positive payload exhaustion uses the recoverable resource subtype`() {
        assertThrows(MigrationPayloadLimitException::class.java) {
            checkedTonUpgradePayloadByteSum(Long.MAX_VALUE - 1, 2, "copy")
        }
        val invalid = assertThrows(TonUpgradeSqlIntegrityException::class.java) {
            checkedTonUpgradePayloadByteSum(-1, 2, "invalid")
        }
        assertTrue(invalid !is MigrationPayloadLimitException)
        val invalidLength = assertThrows(TonUpgradeSqlIntegrityException::class.java) {
            requireBoundedHistoricalPayload(payloadDatabase(listOf(listOf(-1L))), "example", listOf("a"), 1, 0, LIMITS)
        }
        assertTrue(invalidLength !is MigrationPayloadLimitException)
        assertThrows(MigrationPayloadLimitException::class.java) {
            requireBoundedHistoricalPayload(payloadDatabase(listOf(listOf(LIMITS.maximumCellBytes + 1))), "example", listOf("a"), 1, 0, LIMITS)
        }
    }

    private fun payloadDatabase(rows: List<List<Any?>>): SupportSQLiteDatabase = mock {
        on { query(any<String>()) } doAnswer { lengthCursor(rows) }
    }

    @Test
    fun `DB31 raw accounting includes unselected and malformed node payloads before any SQL copy`() {
        val database: SupportSQLiteDatabase = mock {
            on { query(any<String>()) } doAnswer { invocation ->
                val sql = invocation.arguments[0] as String
                when {
                    sql.startsWith("SELECT length") && sql.contains("FROM `chain_nodes`") -> {
                        assertTrue("Raw archive accounting must include every node", !sql.contains(" WHERE "))
                        lengthCursor(listOf(List(5) { 2L }, List(5) { 3L }))
                    }
                    sql.startsWith("SELECT length") && sql.contains("FROM `chains`") -> lengthCursor(listOf(List(16) { 3L }))
                    sql.startsWith("SELECT 1 FROM `") -> lengthCursor(emptyList())
                    else -> error("Unexpected query: $sql")
                }
            }
        }
        assertEquals(73L, HistoricalNodeUpgradePreflight.requireBoundedDb31Payloads(database, LIMITS))
        verify(database, never()).execSQL(any())
    }

    @Test
    fun `DB31 raw node exhaustion fails before reading parents or writing preview SQL`() {
        val database: SupportSQLiteDatabase = mock {
            on { query(any<String>()) } doAnswer { invocation ->
                val sql = invocation.arguments[0] as String
                when {
                    sql.startsWith("SELECT length") && sql.contains("FROM `chain_nodes`") -> lengthCursor(listOf(listOf(1_001L, 1L, 1L, 1L, 1L)))
                    sql.startsWith("SELECT 1 FROM `") -> lengthCursor(emptyList())
                    else -> error("Parent payload must not be copied or queried after raw node exhaustion: $sql")
                }
            }
        }
        assertThrows(MigrationPayloadLimitException::class.java) {
            HistoricalNodeUpgradePreflight.requireBoundedDb31Payloads(database, LIMITS)
        }
        verify(database, never()).execSQL(any())
    }

    private fun nodeDatabase(
        retained: Boolean,
        invalidFlags: Boolean = false,
        transactionOpen: Boolean = false
    ): SupportSQLiteDatabase = mock {
        on { inTransaction() } doAnswer { transactionOpen }
        on { query(any<String>()) } doAnswer { invocation ->
            val sql = invocation.arguments[0] as String
            when {
                sql.contains("sqlite_temp_master") || sql.contains("FROM main.sqlite_master") -> lengthCursor(emptyList())
                sql == "PRAGMA foreign_keys" -> lengthCursor(listOf(listOf(1L)))
                sql.contains("typeof(isDefault)") -> lengthCursor(if (invalidFlags) listOf(listOf(1L)) else emptyList())
                sql.contains("WHERE isDefault = 0 OR isActive = 1") -> lengthCursor(if (retained) listOf(listOf(1L)) else emptyList())
                sql.startsWith("SELECT 1 FROM `chain_nodes`") -> lengthCursor(emptyList())
                else -> error("Unexpected query: $sql")
            }
        }
    }

    private fun lengthCursor(rows: List<List<Any?>>): Cursor {
        var row = -1
        return mock {
            on { moveToFirst() } doAnswer { row = 0; rows.isNotEmpty() }
            on { moveToNext() } doAnswer { ++row < rows.size }
            on { isNull(any()) } doAnswer { rows[row][it.arguments[0] as Int] == null }
            on { getType(any()) } doAnswer {
                if (rows[row][it.arguments[0] as Int] is Long) Cursor.FIELD_TYPE_INTEGER else Cursor.FIELD_TYPE_STRING
            }
            on { getLong(any()) } doAnswer { rows[row][it.arguments[0] as Int] as Long }
        }
    }

    private companion object {
        val LIMITS = TonUpgradePayloadLimits(1_000, 2_000, 10_000)
    }
}
