package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class WalletCustodyMigrationTest {
    @Test
    fun `77 to 78 creates an empty custody table without classifying legacy wallets`() {
        val database = mock<SupportSQLiteDatabase>()
        WalletCustodyMigration.migrate(database)

        val sql = argumentCaptor<String>()
        verify(database).execSQL(sql.capture())
        assertEquals(77, WalletCustodyMigration.startVersion)
        assertEquals(78, WalletCustodyMigration.endVersion)
        assertTrue(sql.firstValue.contains("CREATE TABLE IF NOT EXISTS `wallet_custody`"))
        assertTrue(sql.firstValue.contains("ON UPDATE NO ACTION ON DELETE CASCADE"))
        assertFalse(sql.firstValue.contains("INSERT"))
        assertFalse(sql.firstValue.contains("UPDATE `meta_accounts`"))
    }
}
