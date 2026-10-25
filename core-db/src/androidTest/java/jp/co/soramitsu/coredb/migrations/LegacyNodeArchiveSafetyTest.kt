package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Actual SQLite metadata and archive preservation; full Room paths have separate fixtures. */
class LegacyNodeArchiveSafetyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()

    @After
    fun removeOwnedDatabases() { names.forEach(context::deleteDatabase) }

    @Test
    fun fourReleasedOrderAndDefaultCohortsPreserveOpaqueValuesAndSequence() {
        COHORTS.forEach { (order, default) ->
            withDatabase { db ->
                db.createNodes(order, default)
                db.execSQL("INSERT INTO nodes(id,name,link,networkType,isDefault,isActive) VALUES(23,X'00FF',?, 'unknown-network', 'unknown-owner', 2)", arrayOf("wss://offline.invalid/Exact?Case=Yes 雪"))
                val before = db.archive()
                repeat(2) { assertTrue(LegacyNodeArchive.requireSafe(db)); assertEquals(before, db.archive()) }
                assertFalse(db.inTransaction())
            }
        }
    }

    @Test
    fun absentArchiveIsReadOnlyAndDoesNotInventAnEndpointTable() = withDatabase { db ->
        val before = db.rows("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY rowid")
        assertFalse(LegacyNodeArchive.requireSafe(db))
        assertEquals(before, db.rows("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY rowid"))
    }

    @Test
    fun unshippedDefaultAndOrderCrossProductsAreRejectedWithoutChanges() {
        listOf(COHORTS[1].first to true, COHORTS[3].first to false).forEach { (order, default) ->
            withDatabase { db ->
                db.createNodes(order, default)
                val before = db.archive()
                assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(db) }
                assertEquals(before, db.archive())
            }
        }
    }

    @Test
    fun extraOwnedIndexesAndTriggersAreRetainedAndRejected() {
        listOf("CREATE INDEX retained_node_index ON nodes(link)",
            "CREATE TRIGGER retained_node_trigger AFTER INSERT ON nodes BEGIN SELECT 1; END").forEach { ddl ->
            withDatabase { db ->
                db.createNodes(COHORTS[0].first, false)
                db.execSQL(ddl)
                val before = db.archive()
                assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(db) }
                assertEquals(before, db.archive())
            }
        }
    }

    @Test
    fun temporaryShadowsAndCaseVariantTablesNeverBecomeTheArchive() {
        withDatabase { db ->
            db.createNodes(COHORTS[0].first, false)
            db.execSQL("CREATE TEMP TABLE NoDeS(note TEXT)")
            db.execSQL("INSERT INTO temp.NoDeS VALUES('unowned temporary value')")
            val before = db.archive()
            assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(db) }
            assertEquals(before, db.archive())
            assertEquals(listOf(listOf("3:unowned temporary value")), db.rows("SELECT note FROM temp.NoDeS"))
        }
        withDatabase { db ->
            db.execSQL("CREATE TABLE NoDeS(note TEXT)")
            db.execSQL("INSERT INTO NoDeS VALUES('unowned persistent value')")
            assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(db) }
            assertEquals(listOf(listOf("3:unowned persistent value")), db.rows("SELECT note FROM NoDeS"))
        }
    }

    @Test
    fun archiveForeignKeysAndViewsAreNotAdmittedAsOrdinaryReleasedNodes() {
        withDatabase { db ->
            db.execSQL("CREATE TABLE parent(id INTEGER PRIMARY KEY)")
            db.execSQL("CREATE TABLE nodes(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,link TEXT NOT NULL,networkType INTEGER NOT NULL,isDefault INTEGER NOT NULL,isActive INTEGER NOT NULL,FOREIGN KEY(id) REFERENCES parent(id))")
            val before = db.archive()
            assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(db) }
            assertEquals(before, db.archive())
        }
        withDatabase { db ->
            db.execSQL("CREATE VIEW nodes AS SELECT 1 AS id")
            assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(db) }
            assertEquals(listOf(listOf("1:1")), db.rows("SELECT id FROM nodes"))
        }
    }

    @Test
    fun invalidStoredFlagsRemainRawWithoutCreatingOperationalRows() = withDatabase { db ->
        db.createNodes(COHORTS[0].first, false)
        db.execSQL("INSERT INTO nodes(id,name,link,networkType,isDefault,isActive) VALUES(31,X'00FF','offline',0,'unknown',2)")
        db.execSQL("CREATE TABLE chain_nodes(chainId TEXT NOT NULL,url TEXT NOT NULL,name TEXT NOT NULL,PRIMARY KEY(chainId,url))")
        val before = db.archive()
        assertTrue(LegacyNodeArchive.requireSafe(db))
        LegacyNodeMigration.migrate(db)
        assertEquals(before, db.archive())
        assertEquals(0L, db.scalar("SELECT COUNT(*) FROM chain_nodes"))
        assertFalse(db.inTransaction())
    }

    @Test
    fun archiveAboveCandidateDiscoveryLimitRemainsExactWithoutCopyingRows() = withDatabase { db ->
        db.createNodes(COHORTS[0].first, false)
        db.execSQL("CREATE TABLE chain_nodes(chainId TEXT NOT NULL,url TEXT NOT NULL,name TEXT NOT NULL,PRIMARY KEY(chainId,url))")
        val count = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.maximumRowsByTable.getValue("chain_nodes") + 1
        db.beginTransaction()
        try {
            db.compileStatement("INSERT INTO nodes(id,name,link,networkType,isDefault,isActive) VALUES(?,'Saved','offline',15,0,1)").use { insert ->
                repeat(count) { index -> insert.bindLong(1, index.toLong() + 1); insert.executeInsert() }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        val sequence = db.rows("SELECT name,seq FROM sqlite_sequence WHERE name='nodes'")
        assertTrue(LegacyNodeArchive.requireSafe(db))
        LegacyNodeMigration.migrate(db)
        assertEquals(count.toLong(), db.scalar("SELECT COUNT(*) FROM nodes"))
        assertEquals(count.toLong(), db.scalar("SELECT COUNT(*) FROM nodes WHERE id=rowid AND name='Saved' AND link='offline' AND typeof(networkType)='integer' AND networkType=15 AND isDefault=0 AND isActive=1"))
        assertEquals(1L, db.scalar("SELECT MIN(rowid) FROM nodes"))
        assertEquals(count.toLong(), db.scalar("SELECT MAX(rowid) FROM nodes"))
        assertEquals(sequence, db.rows("SELECT name,seq FROM sqlite_sequence WHERE name='nodes'"))
        assertEquals(0L, db.scalar("SELECT COUNT(*) FROM chain_nodes"))
    }

    private fun withDatabase(action: (SupportSQLiteDatabase) -> Unit) {
        val name = "legacy-node-archive-safety-${names.size}"
        names += name
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected test upgrade")
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build())
        try { action(helper.writableDatabase) } finally { helper.close() }
    }

    private fun SupportSQLiteDatabase.createNodes(order: List<String>, activeDefault: Boolean) {
        val columns = order.map { column ->
            when (column) {
                "id" -> "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"
                "name", "link" -> "$column TEXT NOT NULL"
                "isActive" -> "isActive INTEGER NOT NULL" + if (activeDefault) " DEFAULT 0" else ""
                else -> "$column INTEGER NOT NULL"
            }
        }
        execSQL("CREATE TABLE nodes(${columns.joinToString()})")
    }

    private fun SupportSQLiteDatabase.archive() =
        rows("SELECT rowid,* FROM main.nodes ORDER BY rowid") +
            rows("SELECT type,name,tbl_name,sql FROM main.sqlite_master WHERE tbl_name='nodes' ORDER BY rowid") +
            rows("SELECT name,seq FROM main.sqlite_sequence WHERE name='nodes'")

    private fun SupportSQLiteDatabase.scalar(sql: String): Long = query(sql).use {
        assertTrue(it.moveToFirst()); val value = it.getLong(0); assertFalse(it.moveToNext()); value
    }

    private fun SupportSQLiteDatabase.rows(sql: String): List<List<String>> = query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(List(cursor.columnCount) { index ->
                val type = cursor.getType(index)
                val value = when (type) {
                    Cursor.FIELD_TYPE_NULL -> "<null>"
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index).toHexString(withPrefix = false)
                    else -> cursor.getString(index)
                }
                "$type:$value"
            })
        }
    }

    private companion object {
        val COHORTS = listOf(
            listOf("id", "name", "link", "networkType", "isDefault", "isActive") to false,
            listOf("name", "link", "networkType", "isDefault", "isActive", "id") to false,
            listOf("id", "name", "link", "networkType", "isDefault", "isActive") to true,
            listOf("name", "link", "networkType", "isDefault", "id", "isActive") to true
        )
    }
}
