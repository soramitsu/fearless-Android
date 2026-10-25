package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ModernEndpointArchiveTest {
    @Test fun `canonical source keeps its normal explicit and primary index provenance`() {
        assertTrue(admit(canonical()))
        assertTrue(admit(canonical().filterNot { it.name == "index_chain_nodes_chainId" }))
    }

    @Test fun `actual appended flag defaults and renamed parent are admitted`() {
        for (parent in listOf("chains", "_chains")) {
            assertTrue(admit(canonical("CREATE TABLE chain_nodes (chainId TEXT NOT NULL,url TEXT NOT NULL,name TEXT NOT NULL,isActive INTEGER NOT NULL DEFAULT 0,isDefault INTEGER NOT NULL DEFAULT 1,PRIMARY KEY(chainId,url),FOREIGN KEY(chainId) REFERENCES $parent(id) ON UPDATE NO ACTION ON DELETE CASCADE)")))
        }
    }

    @Test fun `known reordered repair cohort retains its alternate explicit index`() {
        val sql = "CREATE TABLE chain_nodes(url TEXT NOT NULL,chainId TEXT NOT NULL,isDefault INTEGER NOT NULL DEFAULT 1,isActive INTEGER NOT NULL,name TEXT NOT NULL,PRIMARY KEY(chainId,url),FOREIGN KEY(chainId) REFERENCES chains(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        val source = canonical(sql).map { if (it.name == "index_chain_nodes_chainId") it.copy(name = "index_chain_nodes_url", sql = "CREATE INDEX index_chain_nodes_url ON chain_nodes(url)") else it }
        assertTrue(admit(source))
        assertThrows(IllegalStateException::class.java) { admit(source.map { if (it.name == "index_chain_nodes_url") it.copy(sql = "CREATE INDEX index_chain_nodes_url ON chain_nodes(name)") else it }) }
    }

    @Test fun `raw five cell source has no uniqueness or affinity requirement`() {
        assertFalse(admit(raw()))
        assertFalse(admit(raw("CREATE TABLE chain_nodes (chainId,url,name,isActive,isDefault,FOREIGN KEY(chainId) REFERENCES chains(id) ON UPDATE NO ACTION ON DELETE CASCADE)")))
    }

    @Test fun `quoted identifiers and optional create guard preserve the same bounded grammar`() {
        val ddl = canonical().first().sql!!.replace("CREATE TABLE", "create table IF NOT EXISTS").replace("chain_nodes", "`chain_nodes`").replace("chainId", "[chainId]")
        assertTrue(admit(canonical(ddl)))
    }

    @Test fun `virtual without rowid generated collated and checked layouts reject`() {
        val ddl = canonical().first().sql!!
        listOf(ddl.replace("CREATE TABLE", "CREATE VIRTUAL TABLE"), "$ddl WITHOUT ROWID", "$ddl STRICT",
            ddl.replace("name TEXT NOT NULL", "name TEXT NOT NULL COLLATE NOCASE"),
            ddl.replace("name TEXT NOT NULL", "name TEXT NOT NULL CHECK(length(name)>0)"),
            ddl.replace("name TEXT NOT NULL", "name TEXT GENERATED ALWAYS AS (url) VIRTUAL")
        ).forEach { candidate -> assertThrows(IllegalStateException::class.java) { admit(canonical(candidate)) } }
    }

    @Test fun `unknown defaults column order extra columns and rowid shadow reject`() {
        val ddl = canonical().first().sql!!
        listOf(ddl.replace("isActive INTEGER NOT NULL", "isActive INTEGER NOT NULL DEFAULT 9"),
            ddl.replace("name TEXT NOT NULL,isActive", "isActive INTEGER NOT NULL,name"),
            ddl.replace("name TEXT NOT NULL", "name TEXT NOT NULL,rowid INTEGER"),
            ddl.replace("isDefault INTEGER NOT NULL", "unreleased INTEGER NOT NULL")
        ).forEach { candidate -> assertThrows(IllegalStateException::class.java) { admit(canonical(candidate)) } }
    }

    @Test fun `missing primary index wrong owner or a named view cannot be source provenance`() {
        listOf(canonical().filterNot { it.name == "sqlite_autoindex_chain_nodes_1" },
            canonical().map { if (it.type == "table") it.copy(owner = "other") else it },
            canonical().map { if (it.type == "table") it.copy(type = "view") else it },
            canonical().map { if (it.type == "table") it.copy(name = "Chain_Nodes") else it }
        ).forEach { candidate -> assertThrows(IllegalStateException::class.java) { admit(candidate) } }
    }

    @Test fun `duplicate metadata identities triggers and unrelated indexes reject`() {
        val source = canonical()
        listOf(source + source.first(), source.map { it.copy(rowid = 1) },
            raw() + ModernEndpointSchemaRow(2, "trigger", "owner_trigger", "chain_nodes", "CREATE TRIGGER owner_trigger"),
            source.map { if (it.name == "index_chain_nodes_chainId") it.copy(name = "unreleased_index") else it }
        ).forEach { candidate -> assertThrows(IllegalStateException::class.java) { admit(candidate) } }
    }

    @Test fun `explicit index expression direction collation uniqueness and wrong column reject`() {
        val source = canonical()
        listOf("CREATE UNIQUE INDEX index_chain_nodes_chainId ON chain_nodes(chainId)",
            "CREATE INDEX index_chain_nodes_chainId ON chain_nodes(url)",
            "CREATE INDEX index_chain_nodes_chainId ON chain_nodes(chainId DESC)",
            "CREATE INDEX index_chain_nodes_chainId ON chain_nodes(chainId COLLATE NOCASE)",
            "CREATE INDEX index_chain_nodes_chainId ON chain_nodes(lower(chainId))"
        ).forEach { sql -> assertThrows(IllegalStateException::class.java) { admit(source.map { if (it.name == "index_chain_nodes_chainId") it.copy(sql = sql) else it }) } }
    }

    @Test fun `unsafe bounded metadata tokens and truncated identifiers reject`() {
        listOf("CREATE TABLE chain_nodes (name 'secret')", "CREATE TABLE `chain_nodes (chainId)",
            "CREATE TABLE chain_nodes /* hidden */ (chainId)", "CREATE TABLE chain_nodes (chainId);DROP TABLE chains",
            " ".repeat(16_385)
        ).forEach { sql -> assertThrows(IllegalStateException::class.java) { admit(raw(sql)) } }
    }

    @Test fun `absent archive admission only queries bounded metadata and never starts transaction`() {
        val queries = mutableListOf<String>()
        val db = mock<SupportSQLiteDatabase> { on { query(any<String>()) } doAnswer { queries += it.arguments[0] as String; emptyCursor() } }
        assertFalse(ModernEndpointArchive.requireSafe(db))
        assertEquals(2, queries.size)
        assertTrue(queries.all { "sqlite_temp_master" in it || "main.sqlite_master" in it })
        verify(db, never()).beginTransaction()
        verify(db, never()).execSQL(any<String>())
    }

    @Test fun `unsupported edge rejects before database interactions`() {
        val db = mock<SupportSQLiteDatabase>()
        assertThrows(IllegalArgumentException::class.java) { ModernEndpointArchive.capture(db, 31) }
        verifyNoInteractions(db)
    }

    @Test fun `source absent is no capture with balanced owned transaction and no writes`() {
        val db = mock<SupportSQLiteDatabase> { on { query(any<String>()) } doAnswer { emptyCursor() } }
        assertFalse(ModernEndpointArchive.capture(db, 76))
        verify(db).beginTransaction(); verify(db).setTransactionSuccessful(); verify(db).endTransaction()
        verify(db, never()).execSQL(any<String>())
        verify(db, never()).execSQL(any<String>(), any<Array<Any?>>())
    }

    @Test fun `metadata failure propagates and owned cleanup cannot replace original error`() {
        val original = IllegalStateException("metadata failure")
        val cleanup = IllegalStateException("rollback failure")
        val db = mock<SupportSQLiteDatabase> { on { query(any<String>()) } doAnswer { throw original } }
        whenever(db.endTransaction()).thenThrow(cleanup)
        val observed = assertThrows(IllegalStateException::class.java) { ModernEndpointArchive.capture(db, 39) }
        assertSame(original, observed); assertEquals(listOf(cleanup), observed.suppressed.toList())
        verify(db, never()).setTransactionSuccessful()
    }

    @Test fun `caller transaction is neither committed nor ended on admission failure`() {
        val original = IllegalStateException("metadata failure")
        val db = mock<SupportSQLiteDatabase> {
            on { inTransaction() } doAnswer { true }
            on { query(any<String>()) } doAnswer { throw original }
        }
        assertSame(original, assertThrows(IllegalStateException::class.java) { ModernEndpointArchive.capture(db, 39) })
        verify(db, never()).beginTransaction(); verify(db, never()).setTransactionSuccessful(); verify(db, never()).endTransaction()
    }

    @Test fun `first capture completes header only after copied row and schema checks`() {
        val fixture = CopyFixture()
        assertTrue(ModernEndpointArchive.capture(fixture.db, 39))
        assertTrue(fixture.headerWritten)
        assertTrue(ModernEndpointArchive.requireSafe(fixture.db))
        assertEquals(39L, fixture.header[2]); assertEquals(31L, fixture.header[3])
        verify(fixture.db).setTransactionSuccessful()
    }

    @Test fun `earliest complete capture never reads a later operational source`() {
        val fixture = CopyFixture()
        assertTrue(ModernEndpointArchive.capture(fixture.db, 39))
        fixture.forbidSourceReads = true
        assertFalse(ModernEndpointArchive.capture(fixture.db, 76))
        assertEquals(39L, fixture.header[2])
    }

    @Test fun `copy mismatch never marks header complete or commits`() {
        val fixture = CopyFixture(copyMismatch = true)
        val error = assertThrows(IllegalStateException::class.java) { ModernEndpointArchive.capture(fixture.db, 39) }
        assertTrue(error.message.orEmpty().contains("storage class or value"))
        assertFalse(fixture.headerWritten)
        verify(fixture.db, never()).setTransactionSuccessful()
    }

    @Test fun `bounded scalar lengths reject oversized name before creating any table`() {
        val fixture = CopyFixture(nameBytes = 3)
        val error = assertThrows(MigrationPayloadLimitException::class.java) {
            ModernEndpointArchive.capture(fixture.db, 39, TonUpgradePayloadLimits(2, 8, 16))
        }
        assertTrue(error.message.orEmpty().contains("chain_nodes.name"))
        assertTrue(fixture.created.isEmpty())
    }

    @Test fun `header integer types versions counts and payload totals are checked independently`() {
        for ((position, value) in listOf(0 to 2L, 1 to 2L, 2 to 31L, 3 to -1L, 4 to 2L, 5 to 99L, 6 to 2L)) {
            val fixture = CopyFixture()
            assertTrue(ModernEndpointArchive.capture(fixture.db, 39))
            fixture.header[position] = value
            assertThrows(IllegalStateException::class.java) { ModernEndpointArchive.requireSafe(fixture.db) }
        }
    }

    /** Does not implement row values; any accidental payload decoding fails immediately. */
    private class CopyFixture(private val copyMismatch: Boolean = false, private val nameBytes: Long = 1) {
        val created = linkedMapOf<String, String>()
        var headerWritten = false
        var forbidSourceReads = false
        val header = mutableListOf<Long>()
        private var metadataWritten = false
        val db: SupportSQLiteDatabase = mock {
            on { query(any<String>()) } doAnswer { query(it.arguments[0] as String) }
            on { execSQL(any<String>()) } doAnswer {
                val sql = it.arguments[0] as String
                when {
                    sql.startsWith("CREATE TABLE ") -> created[sql.substringAfter("CREATE TABLE ").substringBefore(" ")] = sql
                    sql.startsWith("INSERT INTO main.endpoint_archive_schema_v1") -> metadataWritten = true
                    sql.startsWith("INSERT INTO main.endpoint_archive_v1") -> Unit
                    else -> error("Unexpected mutation: $sql")
                }
                Unit
            }
            on { execSQL(any<String>(), any<Array<Any?>>()) } doAnswer {
                val sql = it.arguments[0] as String
                check(sql.startsWith("INSERT INTO main.endpoint_archive_header_v1"))
                val args = it.arguments[1] as Array<*>
                header.clear(); header += listOf(1L, 1L) + args.map { value -> (value as Number).toLong() }
                headerWritten = true
                Unit
            }
        }
        private fun query(sql: String): Cursor {
            if (forbidSourceReads) check("FROM main.chain_nodes" !in sql && "FROM `chain_nodes`" !in sql && "pragma_table_info" !in sql && "name = 'chain_nodes'" !in sql)
            val result: List<List<Any?>> = when {
                "sqlite_temp_master" in sql -> emptyList()
                "FROM main.sqlite_master" in sql && "name = 'endpoint_archive_v1'" in sql -> created.entries.mapIndexed { index, entry -> listOf(index.toLong() + 1, "table", entry.key, entry.key, "text", entry.value) }
                "FROM main.sqlite_master" in sql -> sourceMetadata()
                "pragma_table_info" in sql -> listOf("chainId", "url", "name", "isActive", "isDefault").mapIndexed { index, name -> listOf(index.toLong(), name, "", 0L, null, 0L) }
                "SELECT COUNT(*)" in sql -> listOf(listOf(1L))
                sql.startsWith("SELECT length(") -> listOf(listOf(1L, 1L, nameBytes, 1L, 1L))
                sql == "PRAGMA main.user_version" -> listOf(listOf(31L))
                sql.startsWith("SELECT 1 FROM main.chain_nodes AS s") -> if (copyMismatch) listOf(listOf(1L)) else emptyList()
                "FROM main.endpoint_archive_schema_v1" in sql -> { check(metadataWritten); sourceMetadata() }
                "FROM main.endpoint_archive_header_v1" in sql -> if (headerWritten) listOf(header.toList()) else emptyList()
                else -> error("Unexpected query or endpoint payload read: $sql")
            }
            return cursor(result)
        }
        private fun sourceMetadata() = listOf(listOf<Any?>(11L, "table", "chain_nodes", "chain_nodes", "text", "CREATE TABLE chain_nodes(chainId,url,name,isActive,isDefault)"))
        private fun cursor(rows: List<List<Any?>>): Cursor {
            var row = -1
            return mock {
                on { moveToFirst() } doAnswer { row = 0; rows.isNotEmpty() }
                on { moveToNext() } doAnswer { ++row < rows.size }
                on { isNull(any()) } doAnswer { rows[row][it.arguments[0] as Int] == null }
                on { getType(any()) } doAnswer { when (rows[row][it.arguments[0] as Int]) { null -> Cursor.FIELD_TYPE_NULL; is Long -> Cursor.FIELD_TYPE_INTEGER; is String -> Cursor.FIELD_TYPE_STRING; else -> error("Unsupported fixture value") } }
                on { getLong(any()) } doAnswer { rows[row][it.arguments[0] as Int] as Long }
                on { getString(any()) } doAnswer { rows[row][it.arguments[0] as Int] as String }
            }
        }
    }

    private fun admit(rows: List<ModernEndpointSchemaRow>) = ModernEndpointArchive.requireSupportedSourceSchema(rows)
    private fun raw(sql: String = "CREATE TABLE chain_nodes(chainId,url,name,isActive,isDefault)") = listOf(ModernEndpointSchemaRow(1,"table","chain_nodes","chain_nodes",sql))
    private fun canonical(sql: String = "CREATE TABLE chain_nodes(chainId TEXT NOT NULL,url TEXT NOT NULL,name TEXT NOT NULL,isActive INTEGER NOT NULL,isDefault INTEGER NOT NULL,PRIMARY KEY(chainId,url),FOREIGN KEY(chainId) REFERENCES chains(id) ON UPDATE NO ACTION ON DELETE CASCADE)") = listOf(
        ModernEndpointSchemaRow(1,"table","chain_nodes","chain_nodes",sql),
        ModernEndpointSchemaRow(2,"index","sqlite_autoindex_chain_nodes_1","chain_nodes",null),
        ModernEndpointSchemaRow(3,"index","index_chain_nodes_chainId","chain_nodes","CREATE INDEX index_chain_nodes_chainId ON chain_nodes(chainId)")
    )
    private fun emptyCursor(): Cursor = mock {
        on { moveToFirst() } doAnswer { false }
        on { moveToNext() } doAnswer { false }
    }
}
