package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class TonUpgradeSqlPreparationPolicyTest {
    @Test fun preparationRequiresTransactionBeforeAnyQueryOrWrite() {
        val database = mock<SupportSQLiteDatabase>()
        assertThrows(IllegalStateException::class.java) { TonUpgradeSqlPreparation.prepare(database) }
        verify(database).inTransaction()
        verifyNoMoreInteractions(database)
    }

    @Test fun repairKeepsIdentityTablesProtectedAndClassifiesUserChoices() {
        val tables = TonUpgradeSqlPreflight.VERSION_71_RELEASED_ROOM_TABLES.map { it.name }.toSet()
        val repaired = RELEASED_V71_REBUILDABLE_TABLES.map { it.contract.name }.toSet()
        assertEquals(setOf("meta_accounts", "chain_accounts", "room_master_table"), tables - repaired)
        assertEquals(setOf("address_book", "assets", "favorite_chains"),
            RELEASED_V71_REBUILDABLE_TABLES.filter { it.rows == RebuildableRows.USER }.map { it.contract.name }.toSet())
    }
}
