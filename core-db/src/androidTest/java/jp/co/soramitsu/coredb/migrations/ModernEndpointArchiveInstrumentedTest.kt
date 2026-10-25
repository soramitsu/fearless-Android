package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Only fresh in-memory databases. No default app DB, preferences, wallet or network. */
@RunWith(AndroidJUnit4::class)
class ModernEndpointArchiveInstrumentedTest {
    @Test fun missingSourceCreatesNoArchiveAndLeavesUnrelatedTablesExact() = withDatabase { db ->
        db.execSQL("CREATE TABLE unrelated(value)")
        db.execSQL("INSERT INTO unrelated VALUES(X'00FF')")
        val before = schema(db)
        assertFalse(ModernEndpointArchive.capture(db, 76))
        assertFalse(ModernEndpointArchive.requireSafe(db))
        assertEquals(before, schema(db))
        assertEquals("00FF", text(db, "SELECT hex(value) FROM unrelated"))
    }

    @Test fun earliestCaptureRetainsRawSourceMetadataAndBothVersionMeaningsAfterParentDeletion() = withDatabase { db ->
        canonical(db)
        db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(-7,'parent','wss://public.invalid','Owner',1,0)")
        db.version = 31 // Room/rollback preview can still expose the original version.
        val original = rows(db, "chain_nodes", "rowid")
        val metadata = schema(db).filter { it[2] == "chain_nodes" }
        assertTrue(ModernEndpointArchive.capture(db, 39))
        assertEquals(original, rows(db, ARCHIVE, "source_rowid"))
        assertEquals(metadata, db.query("SELECT type,name,tbl_name,sql FROM endpoint_archive_schema_v1 ORDER BY source_schema_rowid").use(::values))
        assertEquals(listOf(listOf("39", "31", "1", "3")), db.query("SELECT source_edge_version,source_user_version,source_row_count,source_schema_count FROM endpoint_archive_header_v1").use(::values))
        val archiveSchema = schema(db).filter { it[2].orEmpty().startsWith("endpoint_archive_") }
        val header = text(db, "SELECT hex(CAST(source_edge_version AS BLOB))||':'||hex(CAST(source_user_version AS BLOB)) FROM endpoint_archive_header_v1")
        db.execSQL("UPDATE chain_nodes SET name='Later catalog projection',isDefault=1")
        assertFalse(ModernEndpointArchive.capture(db, 45))
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("DELETE FROM chains")
        assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM chain_nodes"))
        assertTrue(ModernEndpointArchive.requireSafe(db))
        assertEquals(original, rows(db, ARCHIVE, "source_rowid"))
        assertEquals(archiveSchema, schema(db).filter { it[2].orEmpty().startsWith("endpoint_archive_") })
        assertEquals(header, text(db, "SELECT hex(CAST(source_edge_version AS BLOB))||':'||hex(CAST(source_user_version AS BLOB)) FROM endpoint_archive_header_v1"))
    }

    @Test fun rawCapturePreservesAllFiveStorageClassesDuplicateKeysMalformedTextAndSignedRowids() = withDatabase { db ->
        raw(db)
        db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES " +
            "(-9223372036854775808,NULL,NULL,NULL,NULL,NULL)," +
            "(-7,42,42,42,42,42)," +
            "(0,1.25,1.25,1.25,1.25,1.25)," +
            "(7,CAST(X'4EFF00' AS TEXT),CAST(X'4EFF00' AS TEXT),CAST(X'4EFF00' AS TEXT),CAST(X'4EFF00' AS TEXT),CAST(X'4EFF00' AS TEXT))," +
            "(9223372036854775807,X'00FF',X'00FF',X'00FF',X'00FF',X'00FF')," +
            "(19,'duplicate','url',X'',0,0),(20,'duplicate','url','',1,0)")
        val before = rows(db, "chain_nodes", "rowid")
        assertTrue(ModernEndpointArchive.capture(db, 76))
        assertEquals(before, rows(db, "chain_nodes", "rowid"))
        assertEquals(before, rows(db, ARCHIVE, "source_rowid"))
        assertEquals(7L, scalar(db, "SELECT COUNT(*) FROM $ARCHIVE"))
        assertTrue(ModernEndpointArchive.requireSafe(db))
    }

    @Test fun actualAlterAddedFlagsHaveExactOriginalSchemaProvenance() = withDatabase { db ->
        db.execSQL("CREATE TABLE chains(id TEXT NOT NULL PRIMARY KEY)")
        db.execSQL("CREATE TABLE chain_nodes(chainId TEXT NOT NULL,url TEXT NOT NULL,name TEXT NOT NULL,PRIMARY KEY(chainId,url),FOREIGN KEY(chainId) REFERENCES chains(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_chain_nodes_chainId ON chain_nodes(chainId)")
        db.execSQL("ALTER TABLE chain_nodes ADD COLUMN isActive INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chain_nodes ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 1")
        val original = text(db, "SELECT sql FROM sqlite_master WHERE name='chain_nodes'")
        assertTrue(ModernEndpointArchive.capture(db, 39))
        assertEquals(original, text(db, "SELECT sql FROM endpoint_archive_schema_v1 WHERE name='chain_nodes'"))
        assertTrue(ModernEndpointArchive.requireSafe(db))
    }

    @Test fun knownReorderedRepairLayoutCapturesEveryOriginalValueAndItsAlternateIndex() = withDatabase { db ->
        db.execSQL("CREATE TABLE chains(id TEXT NOT NULL PRIMARY KEY)")
        db.execSQL("INSERT INTO chains VALUES('parent')")
        db.execSQL("CREATE TABLE chain_nodes(url TEXT NOT NULL,chainId TEXT NOT NULL,isDefault INTEGER NOT NULL DEFAULT 1,isActive INTEGER NOT NULL,name TEXT NOT NULL,PRIMARY KEY(chainId,url),FOREIGN KEY(chainId) REFERENCES chains(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_chain_nodes_url ON chain_nodes(url)")
        db.execSQL("INSERT INTO chain_nodes(rowid,url,chainId,isDefault,isActive,name) VALUES(-7,'public','parent',0,1,'Owner')")
        val before = rows(db, "chain_nodes", "rowid")
        val metadata = schema(db).filter { it[2] == "chain_nodes" }
        assertTrue(ModernEndpointArchive.capture(db, 76))
        assertEquals(before, rows(db, ARCHIVE, "source_rowid"))
        assertEquals(metadata, db.query("SELECT type,name,tbl_name,sql FROM endpoint_archive_schema_v1 ORDER BY source_schema_rowid").use(::values))
        assertTrue(ModernEndpointArchive.requireSafe(db))
    }

    @Test fun partialArchiveOrTemporaryShadowIsNeverOverwritten() = withDatabase { db ->
        raw(db)
        db.execSQL("INSERT INTO chain_nodes VALUES('public','url','owner',0,0)")
        db.execSQL("CREATE TABLE endpoint_archive_header_v1(value)")
        db.execSQL("INSERT INTO endpoint_archive_header_v1 VALUES(X'00FF')")
        val before = schema(db)
        val source = rows(db, "chain_nodes", "rowid")
        assertThrows(IllegalStateException::class.java) { ModernEndpointArchive.capture(db, 39) }
        assertEquals(before, schema(db)); assertEquals(source, rows(db, "chain_nodes", "rowid"))
        assertEquals("00FF", text(db, "SELECT hex(value) FROM endpoint_archive_header_v1"))
        db.execSQL("DROP TABLE endpoint_archive_header_v1")
        db.execSQL("CREATE TEMP TABLE chain_nodes(value)")
        assertThrows(IllegalStateException::class.java) { ModernEndpointArchive.capture(db, 39) }
        assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE name LIKE 'endpoint_archive_%'"))
    }

    @Test fun failureAfterCopyRollsBackOwnedTransactionWithoutPartialArchive() = withDatabase { db ->
        raw(db)
        db.execSQL("INSERT INTO chain_nodes VALUES('public','url','owner',0,0)")
        val source = rows(db, "chain_nodes", "rowid")
        val before = schema(db)
        val injected = IllegalStateException("injected header write failure")
        val wrapper = object : SupportSQLiteDatabase by db {
            override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
                if (sql.startsWith("INSERT INTO main.endpoint_archive_header_v1")) throw injected
                db.execSQL(sql, bindArgs)
            }
        }
        assertSame(injected, assertThrows(IllegalStateException::class.java) { ModernEndpointArchive.capture(wrapper, 39) })
        assertFalse(db.inTransaction()); assertEquals(before, schema(db)); assertEquals(source, rows(db, "chain_nodes", "rowid"))
    }

    @Test fun callerRollbackRemovesCompletedCaptureAndPreservesOriginalSource() = withDatabase { db ->
        raw(db)
        db.execSQL("INSERT INTO chain_nodes VALUES('public','url','owner',0,0)")
        val before = schema(db); val source = rows(db, "chain_nodes", "rowid")
        db.beginTransaction()
        try {
            assertTrue(ModernEndpointArchive.capture(db, 39))
            assertTrue(db.inTransaction()); assertTrue(ModernEndpointArchive.requireSafe(db))
        } finally { db.endTransaction() }
        assertEquals(before, schema(db)); assertEquals(source, rows(db, "chain_nodes", "rowid"))
        assertFalse(ModernEndpointArchive.requireSafe(db))
    }

    @Test fun boundedOversizeRejectsBeforeArchiveMutationAndRetainsExistingNameDiagnostic() = withDatabase { db ->
        raw(db)
        db.execSQL("INSERT INTO chain_nodes VALUES('p','u',zeroblob(3),0,0)")
        val before = schema(db); val source = rows(db, "chain_nodes", "rowid")
        val error = assertThrows(MigrationPayloadLimitException::class.java) {
            ModernEndpointArchive.capture(db, 39, TonUpgradePayloadLimits(2, 8, 16))
        }
        assertTrue(error.message.orEmpty().contains("chain_nodes.name"))
        assertEquals(before, schema(db)); assertEquals(source, rows(db, "chain_nodes", "rowid"))
    }

    @Test fun changedHeaderAndExtraOwnedObjectRejectWithoutRepair() = withDatabase { db ->
        raw(db); db.execSQL("INSERT INTO chain_nodes VALUES('p','u','n',0,0)")
        assertTrue(ModernEndpointArchive.capture(db, 39))
        db.execSQL("UPDATE endpoint_archive_header_v1 SET source_row_count=2")
        assertThrows(IllegalStateException::class.java) { ModernEndpointArchive.requireSafe(db) }
        db.execSQL("UPDATE endpoint_archive_header_v1 SET source_row_count=1")
        db.execSQL("CREATE INDEX archive_unowned_index ON endpoint_archive_v1(name)")
        assertThrows(IllegalStateException::class.java) { ModernEndpointArchive.capture(db, 45) }
        assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM $ARCHIVE"))
        db.execSQL("DROP INDEX archive_unowned_index")
        assertTrue(ModernEndpointArchive.requireSafe(db))
    }

    private fun withDatabase(block: (SupportSQLiteDatabase) -> Unit) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("No file migration")
            }).build())
        try { block(helper.writableDatabase) } finally { helper.close() }
    }
    private fun raw(db: SupportSQLiteDatabase) = db.execSQL("CREATE TABLE chain_nodes(chainId,url,name,isActive,isDefault)")
    private fun canonical(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE chains(id TEXT NOT NULL PRIMARY KEY)")
        db.execSQL("INSERT INTO chains VALUES('parent')")
        db.execSQL("CREATE TABLE chain_nodes(chainId TEXT NOT NULL,url TEXT NOT NULL,name TEXT NOT NULL,isActive INTEGER NOT NULL,isDefault INTEGER NOT NULL,PRIMARY KEY(chainId,url),FOREIGN KEY(chainId) REFERENCES chains(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_chain_nodes_chainId ON chain_nodes(chainId)")
    }
    private fun schema(db: SupportSQLiteDatabase) = db.query("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY rowid").use(::values)
    private fun rows(db: SupportSQLiteDatabase, table: String, id: String): List<List<String?>> {
        check(table in listOf("chain_nodes", ARCHIVE) && id in listOf("rowid", "source_rowid"))
        return db.query("SELECT $id," + FIELDS.joinToString { "typeof($it),CASE WHEN typeof($it) IN ('text','blob') THEN hex(CAST($it AS BLOB)) ELSE $it END" } + " FROM main.$table ORDER BY $id").use(::values)
    }
    private fun values(cursor: Cursor): List<List<String?>> = buildList {
        while (cursor.moveToNext()) add((0 until cursor.columnCount).map { index -> when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toRawBits().toString()
            else -> cursor.getString(index)
        } })
    }
    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { assertTrue(it.moveToFirst()); it.getLong(0) }
    private fun text(db: SupportSQLiteDatabase, sql: String): String = db.query(sql).use { assertTrue(it.moveToFirst()); it.getString(0) }
    private companion object {
        const val ARCHIVE = "endpoint_archive_v1"
        val FIELDS = listOf("chainId", "url", "name", "isActive", "isDefault")
    }
}
