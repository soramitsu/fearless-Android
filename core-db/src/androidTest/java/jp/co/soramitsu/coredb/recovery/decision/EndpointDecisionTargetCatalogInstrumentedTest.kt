package jp.co.soramitsu.coredb.recovery.decision

import android.content.Context
import android.database.Cursor
import android.database.CursorWrapper
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.CancellationSignal
import android.os.OperationCanceledException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** Independent catalog-only fixtures; no application database, wallets, preferences or network. */
class EndpointDecisionTargetCatalogInstrumentedTest {
    @Test fun pagesIncludeNodeLessInactiveCatalogTargetsWithExactSignedBoundaries() = fixture { db ->
        for (n in 1..55) insert(db, n.toLong(), id(n), "Network $n")
        val catalog = EndpointDecisionTargetCatalog(db)
        val before = image(db)
        val first = catalog.readPage(CancellationSignal())
        assertEquals((1L..25L).toList(), first.options.map { it.rowId })
        assertNull(first.previousBeforeRowId); assertEquals(25L, first.nextAfterRowId)
        assertTrue(first.options.all { it.target?.family == EndpointDecisionTarget.Family.SUBSTRATE })
        val second = catalog.readPage(CancellationSignal(), afterExclusiveRowId = first.nextAfterRowId)
        assertEquals((26L..50L).toList(), second.options.map { it.rowId })
        val previous = catalog.readPage(CancellationSignal(), beforeExclusiveRowId = second.previousBeforeRowId)
        assertEquals(first.options.map { it.target!!.chainId }, previous.options.map { it.target!!.chainId })
        val last = catalog.readPage(CancellationSignal(), afterExclusiveRowId = second.nextAfterRowId)
        assertEquals((51L..55L).toList(), last.options.map { it.rowId })
        assertNull(last.nextAfterRowId)
        assertTrue(catalog.readPage(CancellationSignal(), afterExclusiveRowId = 55).options.isEmpty())
        assertEquals(before, image(db))
        db.execSQL("DELETE FROM chains")
        insert(db, Long.MIN_VALUE, id(1), "First")
        insert(db, -7, id(2), "Second")
        insert(db, Long.MAX_VALUE, id(3), "Last")
        assertEquals(listOf(Long.MIN_VALUE, -7L, Long.MAX_VALUE),
            catalog.readPage(CancellationSignal()).options.map { it.rowId })
    }

    @Test fun malformedRowsStayVisibleAndOversizedNamesNeverEnterTheCursorWindow() = fixture { db ->
        for (n in 1..5) insert(db, n.toLong(), id(n), "Network $n")
        db.execSQL("UPDATE chains SET id=X'FF' WHERE rowid=1")
        db.execSQL("UPDATE chains SET isEthereumBased='not-a-boolean' WHERE rowid=2")
        db.execSQL("UPDATE chains SET name=CAST(zeroblob(3145728) AS TEXT) WHERE rowid=3")
        db.execSQL("UPDATE chains SET name=CAST(X'FF' AS TEXT) WHERE rowid=4")
        db.execSQL("UPDATE chains SET id='unsupported-local-network' WHERE rowid=5")
        val before = image(db)
        val rows = EndpointDecisionTargetCatalog(db).readPage(CancellationSignal()).options
        assertEquals(5, rows.size)
        assertNull(rows[0].target); assertNull(rows[1].target)
        assertNull(rows[2].name); assertNotNull(rows[2].target)
        assertNull(rows[3].name); assertNotNull(rows[3].target)
        assertEquals(EndpointDecisionTarget.Family.UNSUPPORTED, rows[4].target!!.family)
        assertEquals(before, image(db))
    }

    @Test fun selectedIdentityIsTheCapturedCatalogBindingAndChangesAreObservable() = fixture { db ->
        insert(db, 1, id(1), "Network")
        val catalog = EndpointDecisionTargetCatalog(db)
        val original = catalog.readPage(CancellationSignal()).options.single().target!!
        assertEquals("0x" + id(1), original.expectedGenesis)
        db.execSQL("UPDATE chains SET isTestNet=1")
        val changed = catalog.readPage(CancellationSignal()).options.single().target!!
        assertEquals(original.chainId, changed.chainId)
        assertNotEquals(original.identityFingerprint, changed.identityFingerprint)
        assertFalse(db.inTransaction())
    }

    @Test fun cancellationAfterQueryClosesItsCursorAndLeavesEveryCatalogByteExact() = fixture { db ->
        insert(db, 1, id(1), "Network")
        val signal = CancellationSignal()
        var observed: Cursor? = null
        var queries = 0
        val wrapped = object : SupportSQLiteDatabase by db {
            override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
                return db.query(query, cancellationSignal).also { if (++queries == 2) { observed = it; signal.cancel() } }
            }
        }
        val before = image(db)
        assertThrows(OperationCanceledException::class.java) { EndpointDecisionTargetCatalog(wrapped).readPage(signal) }
        assertTrue(checkNotNull(observed).isClosed)
        assertFalse(db.inTransaction())
        assertEquals(before, image(db))
    }

    @Test fun sqlFailureDuringIdentityCaptureClosesThePageAndDoesNotHideAsAnUnavailableRow() = fixture { db ->
        insert(db, 1, id(1), "Network")
        var observed: Cursor? = null
        var queries = 0
        val wrapped = object : SupportSQLiteDatabase by db {
            override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
                if (++queries == 2) throw SQLiteException("synthetic catalog read failure")
                return db.query(query, cancellationSignal).also { observed = it }
            }
        }
        val before = image(db)
        assertThrows(SQLiteException::class.java) {
            EndpointDecisionTargetCatalog(wrapped).readPage(CancellationSignal())
        }
        assertTrue(checkNotNull(observed).isClosed)
        assertFalse(db.inTransaction())
        assertEquals(before, image(db))
    }

    @Test fun duplicateIdsRemainUnavailableEvenWithoutTheExpectedUniqueIndex() = fixture { db ->
        db.execSQL("DROP TABLE chains")
        db.execSQL("CREATE TABLE chains(id TEXT,name TEXT,ecosystem TEXT,isEthereumBased INTEGER," +
            "isEthereumChain INTEGER,isTestNet INTEGER,history_type TEXT)")
        insert(db, 1, id(1), "First")
        insert(db, 2, id(1), "Second")
        insert(db, 3, id(2), "Unique")
        val before = image(db)
        val page = EndpointDecisionTargetCatalog(db).readPage(CancellationSignal())
        assertEquals(listOf("First", "Second", "Unique"), page.options.map { it.name })
        assertNull(page.options[0].target); assertNull(page.options[1].target)
        assertNotNull(page.options[2].target); assertEquals(before, image(db))
    }

    @Test fun encodingCancellationNeverAttemptsAWriterTransactionOrCatalogPayload() = fixture { db ->
        insert(db, 1, id(1), "Network")
        val signal = CancellationSignal()
        val queries = mutableListOf<String>()
        var observed: Cursor? = null
        val wrapped = object : SupportSQLiteDatabase by db {
            override fun beginTransactionNonExclusive() = error("Unexpected writer transaction")
            override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
                queries += query.sql
                return db.query(query, cancellationSignal).also { observed = it; signal.cancel() }
            }
        }
        val before = image(db)
        assertThrows(OperationCanceledException::class.java) { EndpointDecisionTargetCatalog(wrapped).readPage(signal) }
        assertEquals(listOf("PRAGMA main.encoding"), queries)
        assertTrue(checkNotNull(observed).isClosed); assertFalse(db.inTransaction())
        assertEquals(before, image(db))
    }

    @Test fun actualUtf16DatabaseRejectsBeforeAnyCatalogPayloadQuery() = fixture(encoding = "UTF-16le") { db ->
        insert(db, 1, id(1), "Network")
        db.query("PRAGMA main.encoding").use { assertTrue(it.moveToFirst()); assertEquals("UTF-16le", it.getString(0)) }
        val queries = mutableListOf<String>()
        val wrapped = object : SupportSQLiteDatabase by db {
            override fun beginTransactionNonExclusive() = error("Unexpected writer transaction")
            override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
                queries += query.sql
                return db.query(query, cancellationSignal)
            }
        }
        val before = image(db)
        val error = assertThrows(IllegalStateException::class.java) { EndpointDecisionTargetCatalog(wrapped).readPage(CancellationSignal()) }
        assertEquals("Endpoint catalog requires UTF-8 database encoding", error.message)
        assertEquals(listOf("PRAGMA main.encoding"), queries)
        assertFalse(db.inTransaction()); assertEquals(before, image(db))
    }

    @Test fun nameIdentityAndNavigationComeFromOneRowSnapshotDespiteLaterCatalogEdit() = fixture { db ->
        insert(db, 1, id(1), "Original")
        val original = EndpointDecisionTargetCatalog(db).readPage(CancellationSignal()).options.single().target!!
        var changed = false
        val queries = mutableListOf<String>()
        val wrapped = object : SupportSQLiteDatabase by db {
            override fun beginTransactionNonExclusive() = error("Unexpected writer transaction")
            override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
                queries += query.sql
                val cursor = db.query(query, cancellationSignal)
                if (query.sql == "PRAGMA main.encoding") return cursor
                return object : CursorWrapper(cursor) {
                    override fun getBlob(columnIndex: Int): ByteArray {
                        val value = super.getBlob(columnIndex)
                        if (columnIndex == 2 && !changed) {
                            changed = true
                            db.execSQL("UPDATE chains SET name='Changed',isTestNet=1 WHERE rowid=1")
                            insert(db, 2, id(2), "Later")
                        }
                        return value
                    }
                }
            }
        }
        val page = EndpointDecisionTargetCatalog(wrapped).readPage(CancellationSignal(), pageSize = 1)
        assertTrue(changed); assertEquals(2, queries.size)
        assertEquals("Original", page.options.single().name)
        assertEquals(original.identityFingerprint, page.options.single().target!!.identityFingerprint)
        assertNull(page.nextAfterRowId)
        val current = EndpointDecisionTargetCatalog(db).readPage(CancellationSignal(), pageSize = 1)
        assertEquals("Changed", current.options.single().name)
        assertNotEquals(original.identityFingerprint, current.options.single().target!!.identityFingerprint)
        assertEquals(1L, current.nextAfterRowId); assertFalse(db.inTransaction())
    }

    private fun id(number: Int): String = number.toString(16).padStart(64, '0')

    private fun insert(db: SupportSQLiteDatabase, row: Long, id: String, name: String) {
        db.execSQL("INSERT INTO chains(rowid,id,name,ecosystem,isEthereumBased,isEthereumChain,isTestNet,history_type) " +
            "VALUES(?,?,?,'Substrate',0,0,0,NULL)", arrayOf<Any?>(row, id, name))
    }

    private fun image(db: SupportSQLiteDatabase): List<List<String>> {
        val columns = listOf("id", "name", "ecosystem", "isEthereumBased", "isEthereumChain", "isTestNet", "history_type")
        val rows = db.query("SELECT rowid FROM chains ORDER BY rowid").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        return rows.map { row ->
            buildList {
                add(row.toString())
                for (column in columns) {
                    val metadata = db.query(SimpleSQLiteQuery(
                        "SELECT typeof($column),length(CAST($column AS BLOB)) FROM chains WHERE rowid=?", arrayOf(row)
                    )).use { cursor ->
                        check(cursor.moveToFirst())
                        cursor.getString(0) to if (cursor.isNull(1)) null else cursor.getLong(1)
                    }
                    add(metadata.first); add(metadata.second?.toString() ?: "NULL")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var offset = 0L
                    while (offset < (metadata.second ?: 0)) {
                        val bytes = db.query(SimpleSQLiteQuery(
                            "SELECT substr(CAST($column AS BLOB),?,32768) FROM chains WHERE rowid=?", arrayOf(offset + 1, row)
                        )).use { cursor -> check(cursor.moveToFirst()); cursor.getBlob(0) }
                        check(bytes.isNotEmpty() && bytes.size <= 32768)
                        digest.update(bytes); offset += bytes.size
                    }
                    assertEquals(metadata.second ?: 0, offset)
                    add(digest.digest().joinToString("") { "%02x".format(it) })
                }
            }
        }
    }

    private fun fixture(encoding: String? = null, test: (SupportSQLiteDatabase) -> Unit) {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "endpoint-catalog-${UUID.randomUUID()}.db"
        val create = "CREATE TABLE chains(id TEXT PRIMARY KEY NOT NULL,name TEXT,ecosystem TEXT," +
            "isEthereumBased INTEGER,isEthereumChain INTEGER,isTestNet INTEGER,history_type TEXT)"
        if (encoding != null) {
            check(encoding == "UTF-16le")
            val path = context.getDatabasePath(name)
            check(path.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
            // Establish encoding before Android creates its locale metadata table.
            SQLiteDatabase.openDatabase(path.path, null,
                SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { seed ->
                seed.execSQL("PRAGMA encoding='UTF-16le'")
                seed.execSQL(create)
                seed.version = 1
            }
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(create)
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("No upgrade")
                }).build()
        )
        try { test(helper.writableDatabase) } finally { helper.close(); context.deleteDatabase(name) }
    }
}
