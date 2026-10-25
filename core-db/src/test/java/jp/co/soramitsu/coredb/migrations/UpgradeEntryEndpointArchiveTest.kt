package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class UpgradeEntryEndpointArchiveTest {
    @Test fun `first destructive edge follows the original version exactly`() {
        for ((version,edge) in listOf(0 to 39,28 to 39,39 to 39,40 to 41,41 to 41,42 to 45,45 to 45,46 to 53,53 to 53,54 to 71,71 to 71,72 to 76,76 to 76)) {
            assertEquals(edge,UpgradeEntryEndpointArchive.firstEdge(version))
        }
        assertNull(UpgradeEntryEndpointArchive.firstEdge(77))
        assertNull(UpgradeEntryEndpointArchive.firstEdge(Int.MAX_VALUE))
        assertThrows(IllegalArgumentException::class.java) { UpgradeEntryEndpointArchive.firstEdge(-1) }
    }
    @Test fun `stored legacy captures remain readable only for their original source range`() {
        for (version in 54..71) {
            assertTrue(UpgradeEntryEndpointArchive.admitsStoredCapture(version, 71))
            assertTrue(UpgradeEntryEndpointArchive.admitsStoredCapture(version, 76))
            assertFalse(UpgradeEntryEndpointArchive.admitsStoredCapture(version, 53))
        }
        assertFalse(UpgradeEntryEndpointArchive.admitsStoredCapture(53, 76))
        assertFalse(UpgradeEntryEndpointArchive.admitsStoredCapture(72, 71))
        assertFalse(UpgradeEntryEndpointArchive.admitsStoredCapture(77, 76))
        assertTrue(UpgradeEntryEndpointArchive.admitsStoredCapture(72, 76))
    }
    @Test fun `invalid entry edge rejects without accessing database`() {
        val db=mock<SupportSQLiteDatabase>()
        assertThrows(IllegalArgumentException::class.java) { UpgradeEntryEndpointArchive.beforeDestructiveEdge(db,81) }
        verifyNoInteractions(db)
    }
    @Test fun `absent entry at later edge cannot silently capture newer cache rows`() {
        val db=mock<SupportSQLiteDatabase> {
            on { version } doReturn 40
            on { query(any<String>()) } doAnswer { mock<Cursor> { on { moveToFirst() } doReturn false;on { moveToNext() } doReturn false } }
        }
        UpgradeEntryEndpointArchive.beforeDestructiveEdge(db,45)
        assertFalse(UpgradeEntryEndpointArchive.requireSafeForSource(db))
        verify(db,never()).execSQL(any<String>())
        verify(db,never()).execSQL(any<String>(),any<Array<Any?>>())
    }
}
