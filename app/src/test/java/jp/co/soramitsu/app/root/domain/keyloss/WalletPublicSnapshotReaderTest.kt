package jp.co.soramitsu.app.root.domain.keyloss

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WalletPublicSnapshotReaderTest {
    @Test fun `missing public database returns an empty snapshot without creating a database`() {
        val context = mock<Context>()
        val file = mock<File>()
        whenever(context.getDatabasePath("app.db")).thenReturn(file)
        whenever(file.exists()).thenReturn(false)
        assertEquals(WalletPublicSnapshot(emptyList(), false), WalletPublicSnapshotReader(context).read())
    }

    @Test fun `public database size does not exclude a bounded readonly snapshot`() {
        val context = mock<Context>()
        val file = mock<File>()
        val database = mock<SQLiteDatabase>()
        val cursor = mock<Cursor>()
        whenever(context.getDatabasePath("app.db")).thenReturn(file)
        whenever(file.exists()).thenReturn(true)
        whenever(file.path).thenReturn("public-only-fixture.db")
        whenever(file.length()).thenReturn(512L * 1024 * 1024 + 1)
        whenever(database.rawQuery(any(), anyOrNull<Array<String>>(), any<CancellationSignal>())).thenReturn(cursor)
        mockConstruction(CancellationSignal::class.java).use {
            mockStatic(SQLiteDatabase::class.java).use { opened ->
                opened.`when`<SQLiteDatabase> { SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) }
                    .thenReturn(database)
                assertEquals(WalletPublicSnapshot(emptyList(), true), WalletPublicSnapshotReader(context).read())
                opened.verify { SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) }
            }
        }
        verify(file, never()).length()
        verify(database).close()
    }
}
