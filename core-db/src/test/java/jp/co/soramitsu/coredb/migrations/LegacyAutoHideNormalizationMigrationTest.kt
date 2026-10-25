package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class LegacyAutoHideNormalizationMigrationTest {

    @Test
    fun `80 to 81 only clears hidden state of empty asset rows`() {
        val database = mock<SupportSQLiteDatabase>()
        LegacyAutoHideNormalizationMigration.migrate(database)

        val sql = argumentCaptor<String>()
        verify(database).execSQL(sql.capture())
        verifyNoMoreInteractions(database)
        assertEquals(80, LegacyAutoHideNormalizationMigration.startVersion)
        assertEquals(81, LegacyAutoHideNormalizationMigration.endVersion)

        val statement = sql.firstValue
        assertTrue(statement.startsWith("UPDATE `assets` SET `enabled` = NULL\nWHERE `enabled` = 0"))
        assertTrue(statement.contains("AND `markedNotNeed` = 0"))
        BALANCE_COLUMNS.forEach { column ->
            assertTrue(
                "$column must be empty before a hide is cleared",
                statement.contains("AND (`$column` IS NULL OR `$column` = '0')")
            )
        }
        listOf("DELETE", "DROP", "ALTER", "INSERT", "CREATE", "meta_accounts", "chain_accounts")
            .forEach { forbidden -> assertFalse(forbidden, statement.contains(forbidden)) }
        assertEquals(1, Regex("SET").findAll(statement).count())
    }

    private companion object {
        val BALANCE_COLUMNS = listOf(
            "freeInPlanks",
            "reservedInPlanks",
            "miscFrozenInPlanks",
            "feeFrozenInPlanks",
            "bondedInPlanks",
            "redeemableInPlanks",
            "unbondingInPlanks"
        )
    }
}
