package jp.co.soramitsu.coredb.recovery

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class LegacyEndpointInventoryReaderTest {
    @Test
    fun `bounded UTF8 retains exact case unicode empty and embedded NUL`() {
        listOf("", "Exact?Case=Yes", "雪\u0000é").forEach { text ->
            val bytes = text.toByteArray(Charsets.UTF_8)
            val result = decodeLegacyEndpointText(LegacyEndpointStorage.TEXT, bytes.size.toLong(), bytes, 32)
            assertEquals(LegacyEndpointTextState.AVAILABLE, result.state)
            assertEquals(text, result.value)
        }
    }

    @Test
    fun `UTF8 byte bound is exact and oversized payload must never be supplied`() {
        val bytes = "雪".toByteArray(Charsets.UTF_8)
        assertEquals("雪", decodeLegacyEndpointText(LegacyEndpointStorage.TEXT, 3, bytes, 3).value)
        val deferred = decodeLegacyEndpointText(LegacyEndpointStorage.TEXT, 3, null, 2)
        assertEquals(LegacyEndpointTextState.OVERSIZED, deferred.state)
        assertNull(deferred.value)
        assertThrows(IllegalStateException::class.java) {
            decodeLegacyEndpointText(LegacyEndpointStorage.TEXT, 3, bytes, 2)
        }
    }

    @Test
    fun `unsupported stored types remain opaque with their original length`() {
        listOf(LegacyEndpointStorage.BLOB, LegacyEndpointStorage.INTEGER, LegacyEndpointStorage.REAL).forEach { type ->
            val result = decodeLegacyEndpointText(type, 1_048_577, null, 32)
            assertEquals(type, result.storage)
            assertEquals(1_048_577L, result.byteLength)
            assertEquals(LegacyEndpointTextState.UNSUPPORTED_STORAGE, result.state)
            assertNull(result.value)
        }
        assertEquals(LegacyEndpointTextState.NULL,
            decodeLegacyEndpointText(LegacyEndpointStorage.NULL, null, null, 32).state)
    }

    @Test
    fun `invalid UTF8 is disclosed rather than replacement decoded`() {
        val result = decodeLegacyEndpointText(LegacyEndpointStorage.TEXT, 1, byteArrayOf(-128), 32)
        assertEquals(LegacyEndpointTextState.INVALID_UTF8, result.state)
        assertNull(result.value)
    }

    @Test
    fun `inconsistent scalar metadata and projected bytes fail honestly`() {
        listOf<() -> Unit>(
            { decodeLegacyEndpointText(LegacyEndpointStorage.TEXT, -1, null, 32) },
            { decodeLegacyEndpointText(LegacyEndpointStorage.TEXT, null, null, 32) },
            { decodeLegacyEndpointText(LegacyEndpointStorage.NULL, 0, null, 32) },
            { decodeLegacyEndpointText(LegacyEndpointStorage.TEXT, 2, byteArrayOf(1), 32) },
            { decodeLegacyEndpointText(LegacyEndpointStorage.TEXT, 2, null, 32) },
            { decodeLegacyEndpointText(LegacyEndpointStorage.BLOB, 1, byteArrayOf(1), 32) }
        ).forEach { assertThrows(IllegalStateException::class.java) { it() } }
    }

    @Test
    fun `invalid decoder caps are not silently widened`() {
        listOf(0, -1, 2049).forEach { cap ->
            assertThrows(IllegalArgumentException::class.java) {
                decodeLegacyEndpointText(LegacyEndpointStorage.TEXT, 0, byteArrayOf(), cap)
            }
        }
    }

    @Test
    fun `diagnostic string does not leak saved endpoint credentials`() {
        val secret = "wss://user:password@private.invalid/rpc"
        val bytes = secret.toByteArray()
        val value = decodeLegacyEndpointText(LegacyEndpointStorage.TEXT, bytes.size.toLong(), bytes, 2048)
        assertFalse(value.toString().contains(secret))
        assertFalse(value.toString().contains("password"))
        val integer = LegacyEndpointInteger(LegacyEndpointStorage.INTEGER, 1, 0)
        val record = LegacyEndpointRecord(42, value, value, integer, integer, integer)
        assertTrue(record.toString().contains("42"))
        assertFalse(record.toString().contains(secret))
        assertFalse(record.toString().contains("password"))
    }

    @Test
    fun `public page decodes exact bounded text and integer metadata through cancellable schema queries`() {
        val fixture = Fixture(pageRows = listOf(row(-7, "Saved 雪\u0000", "wss://offline.invalid/Exact?Yes")))
        val page = LegacyEndpointInventoryReader(fixture.database).readPage(fixture.signal)
        assertTrue(page.archivePresent)
        val record = page.records.single()
        assertEquals(-7L, record.originalRowId)
        assertEquals("Saved 雪\u0000", record.name.value)
        assertEquals("wss://offline.invalid/Exact?Yes", record.endpoint.value)
        assertEquals(LegacyEndpointInteger(LegacyEndpointStorage.INTEGER, 2, 15), record.networkType)
        assertEquals(LegacyEndpointInteger(LegacyEndpointStorage.INTEGER, 1, 0), record.isDefault)
        assertEquals(LegacyEndpointInteger(LegacyEndpointStorage.INTEGER, 1, 1), record.isActive)
        assertNull(page.previousBeforeRowId)
        assertNull(page.nextAfterRowId)
        fixture.assertReadOnlyAndClosed()
    }

    @Test
    fun `public page preserves opaque storage and oversized text without invented values`() {
        val projected = row(1, "", "") + mapOf(
            "name_bytes" to 2049L, "name_bounded" to null,
            "link_type" to "blob", "link_bytes" to 1_048_577L, "link_bounded" to null,
            "networkType_type" to "text", "networkType_bytes" to 7L, "networkType_integer" to null,
            "isDefault_type" to "null", "isDefault_bytes" to null, "isDefault_integer" to null,
            "isActive_integer" to 2L
        )
        val fixture = Fixture(pageRows = listOf(projected))
        val record = LegacyEndpointInventoryReader(fixture.database).readPage(fixture.signal).records.single()
        assertEquals(LegacyEndpointTextState.OVERSIZED, record.name.state)
        assertNull(record.name.value)
        assertEquals(LegacyEndpointTextState.UNSUPPORTED_STORAGE, record.endpoint.state)
        assertEquals(1_048_577L, record.endpoint.byteLength)
        assertEquals(LegacyEndpointInteger(LegacyEndpointStorage.TEXT, 7, null), record.networkType)
        assertEquals(LegacyEndpointInteger(LegacyEndpointStorage.NULL, null, null), record.isDefault)
        assertEquals(2L, record.isActive.value)
        fixture.assertReadOnlyAndClosed()
    }

    @Test
    fun `public forward page binds exact cursor and computes previous next from bounded probes`() {
        val fixture = Fixture(pageRows = listOf(row(-2), row(0), row(5)), oppositeExists = true)
        val page = LegacyEndpointInventoryReader(fixture.database).readPage(
            fixture.signal, afterExclusiveRowId = Long.MIN_VALUE, pageSize = 2)
        assertEquals(listOf(-2L, 0L), page.records.map { it.originalRowId })
        assertEquals(-2L, page.previousBeforeRowId)
        assertEquals(0L, page.nextAfterRowId)
        val query = fixture.queries.single { it.startsWith("SELECT rowid AS archive_rowid") }
        assertTrue(query.endsWith("WHERE rowid > ? ORDER BY rowid ASC LIMIT 3"))
        assertEquals(listOf(Long.MIN_VALUE), fixture.bindings.getValue(query))
        assertEquals(listOf(-2L), fixture.bindings.getValue("SELECT 1 FROM main.nodes WHERE rowid < ? LIMIT 1"))
        fixture.assertReadOnlyAndClosed()
    }

    @Test
    fun `public reverse page returns ascending rows without overflow arithmetic`() {
        val fixture = Fixture(pageRows = listOf(row(7), row(-9), row(Long.MIN_VALUE)), oppositeExists = true)
        val page = LegacyEndpointInventoryReader(fixture.database).readPage(
            fixture.signal, beforeExclusiveRowId = Long.MAX_VALUE, pageSize = 2)
        assertEquals(listOf(-9L, 7L), page.records.map { it.originalRowId })
        assertEquals(-9L, page.previousBeforeRowId)
        assertEquals(7L, page.nextAfterRowId)
        val query = fixture.queries.single { it.startsWith("SELECT rowid AS archive_rowid") }
        assertTrue(query.endsWith("WHERE rowid < ? ORDER BY rowid DESC LIMIT 3"))
        assertEquals(listOf(Long.MAX_VALUE), fixture.bindings.getValue(query))
        assertEquals(listOf(7L), fixture.bindings.getValue("SELECT 1 FROM main.nodes WHERE rowid > ? LIMIT 1"))
        fixture.assertReadOnlyAndClosed()
    }

    @Test
    fun `public absent empty and unsupported encoding states stay distinct`() {
        val absent = Fixture(present = false)
        assertEquals(LegacyEndpointPage(false, emptyList(), null, null),
            LegacyEndpointInventoryReader(absent.database).readPage(absent.signal))
        val empty = Fixture()
        assertEquals(LegacyEndpointPage(true, emptyList(), null, null),
            LegacyEndpointInventoryReader(empty.database).readPage(empty.signal, beforeExclusiveRowId = Long.MIN_VALUE))
        assertFalse(empty.queries.any { it.startsWith("SELECT 1 FROM main.nodes") })
        val unsupported = Fixture(encoding = "UTF-16le")
        assertThrows(IllegalStateException::class.java) {
            LegacyEndpointInventoryReader(unsupported.database).readPage(unsupported.signal)
        }
        assertEquals(listOf("PRAGMA encoding"), unsupported.queries)
        listOf(absent, empty, unsupported).forEach { it.assertReadOnlyAndClosed() }
    }

    @Test
    fun `public invalid paging arguments fail before metadata query`() {
        val fixture = Fixture()
        val reader = LegacyEndpointInventoryReader(fixture.database)
        listOf(0, -1, 51).forEach { size ->
            assertThrows(IllegalArgumentException::class.java) { reader.readPage(fixture.signal, pageSize = size) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            reader.readPage(fixture.signal, afterExclusiveRowId = 0, beforeExclusiveRowId = 2)
        }
        assertTrue(fixture.queries.isEmpty())
    }

    @Test
    fun `public inconsistent row identity and private field metadata close cursor and fail`() {
        val samples = listOf(
            row(1) + mapOf("archive_rowid" to "1"),
            row(1) + mapOf("name_type" to "unrecognized"),
            row(1) + mapOf("name_bytes" to "5"),
            row(1) + mapOf("name_bounded" to "Saved"),
            row(1) + mapOf("networkType_bytes" to -1L),
            row(1) + mapOf("networkType_integer" to "15"),
            row(1) + mapOf("isActive_integer" to null)
        )
        samples.forEach { sample ->
            val fixture = Fixture(pageRows = listOf(sample))
            assertThrows(IllegalStateException::class.java) {
                LegacyEndpointInventoryReader(fixture.database).readPage(fixture.signal)
            }
            fixture.assertReadOnlyAndClosed()
        }
        val unordered = Fixture(pageRows = listOf(row(2), row(1)))
        assertThrows(IllegalStateException::class.java) {
            LegacyEndpointInventoryReader(unordered.database).readPage(unordered.signal)
        }
        unordered.assertReadOnlyAndClosed()
    }

    @Test
    fun `public metadata page and cancellation failures are propagated without empty success`() {
        listOf("sqlite_temp_master", "SELECT rowid AS archive_rowid").forEach { fragment ->
            val failure = IllegalStateException("Synthetic SQL read failure")
            val fixture = Fixture(pageRows = listOf(row(1)), failureAt = fragment, failure = failure)
            assertSame(failure, assertThrows(IllegalStateException::class.java) {
                LegacyEndpointInventoryReader(fixture.database).readPage(fixture.signal)
            })
            fixture.assertReadOnlyAndClosed()
        }
        val failure = IllegalStateException("Synthetic cancellation from signal")
        val signal: CancellationSignal = mock { on { throwIfCanceled() } doAnswer { throw failure } }
        val fixture = Fixture(signal = signal)
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            LegacyEndpointInventoryReader(fixture.database).readPage(signal)
        })
        assertTrue(fixture.queries.isEmpty())
    }

    @Test
    fun `chunk binds exact signed rowid offset and cap with only closed fields`() {
        LegacyEndpointField.values().forEach { field ->
            val bytes = ByteArray(512) { it.toByte() }
            val fixture = Fixture(chunkRows = listOf(chunkRow(Long.MIN_VALUE, length = 1025, bytes = bytes)))
            val chunk = LegacyEndpointValueChunkReader(fixture.database).readChunk(fixture.signal, Long.MIN_VALUE, field, 512)
            assertEquals(field, chunk.field)
            assertEquals(Long.MIN_VALUE, chunk.originalRowId)
            assertEquals(512L, chunk.byteOffset)
            assertEquals(1024L, chunk.endByteOffsetExclusive)
            assertEquals(0L, chunk.previousByteOffset)
            assertEquals(1024L, chunk.nextByteOffset)
            assertEquals(512, chunk.byteCount)
            assertArrayEquals(bytes, chunk.bytes)
            val query = fixture.queries.single { it.startsWith("SELECT rowid AS chunk_rowid") }
            assertTrue(query.contains("typeof(${field.column})"))
            assertTrue(query.contains("substr(CAST(${field.column} AS BLOB),?,?)"))
            assertTrue(query.endsWith("WHERE rowid = ? LIMIT 1"))
            assertEquals(listOf(513L, 512L, Long.MIN_VALUE), fixture.bindings.getValue(query))
            assertFalse(Regex("\\b(COUNT|OFFSET)\\b", RegexOption.IGNORE_CASE).containsMatchIn(query))
            assertFalse(query.contains("SELECT *"))
            fixture.assertReadOnlyAndClosed()
        }
    }

    @Test
    fun `chunk handles first final empty and long boundary without overflow`() {
        val cases = listOf(
            Triple(0L, 0L, 0), Triple(0L, 513L, 512), Triple(512L, 513L, 1),
            Triple(513L, 513L, 0), Triple(Long.MAX_VALUE - 1, Long.MAX_VALUE, 1)
        )
        cases.forEach { (offset, length, size) ->
            val fixture = Fixture(chunkRows = listOf(chunkRow(Long.MAX_VALUE, type = "blob", length = length, bytes = ByteArray(size))))
            val chunk = LegacyEndpointValueChunkReader(fixture.database).readChunk(fixture.signal, Long.MAX_VALUE, LegacyEndpointField.ENDPOINT, offset)
            assertEquals(LegacyEndpointStorage.BLOB, chunk.storage)
            assertEquals(LegacyEndpointChunkState.AVAILABLE, chunk.state)
            assertEquals(offset + size, chunk.endByteOffsetExclusive)
            assertEquals(if (offset > 0) maxOf(0, offset - 512) else null, chunk.previousByteOffset)
            assertEquals(if (offset + size < length) offset + size else null, chunk.nextByteOffset)
            assertEquals(size, chunk.byteCount)
            fixture.assertReadOnlyAndClosed()
        }
    }

    @Test
    fun `chunk absence null and numeric scalar storage are distinct and opaque`() {
        val absent = Fixture(present = false)
        assertEquals(LegacyEndpointChunkState.ARCHIVE_ABSENT,
            LegacyEndpointValueChunkReader(absent.database).readChunk(absent.signal, -1, LegacyEndpointField.NAME).state)
        assertFalse(absent.queries.any { it.startsWith("SELECT rowid AS chunk_rowid") })
        val missing = Fixture()
        val none = LegacyEndpointValueChunkReader(missing.database).readChunk(missing.signal, -1, LegacyEndpointField.NAME, 7)
        assertEquals(LegacyEndpointChunkState.ROW_ABSENT, none.state)
        assertNull(none.storage)
        assertNull(none.bytes)
        assertNull(none.endByteOffsetExclusive)
        listOf("null", "integer", "real").forEach { type ->
            val fixture = Fixture(chunkRows = listOf(chunkRow(1, type, null, null)))
            val chunk = LegacyEndpointValueChunkReader(fixture.database).readChunk(fixture.signal, 1, LegacyEndpointField.NAME)
            assertEquals(if (type == "null") LegacyEndpointChunkState.NULL_VALUE else LegacyEndpointChunkState.UNSUPPORTED_STORAGE, chunk.state)
            assertNull(chunk.totalByteLength)
            assertNull(chunk.bytes)
            assertNull(chunk.previousByteOffset)
            assertNull(chunk.nextByteOffset)
            fixture.assertReadOnlyAndClosed()
        }
        listOf(absent, missing).forEach { it.assertReadOnlyAndClosed() }
    }

    @Test
    fun `chunk invalid argument and impossible offsets fail without invented data`() {
        val fixture = Fixture()
        val reader = LegacyEndpointValueChunkReader(fixture.database)
        listOf(-1L, Long.MIN_VALUE, Long.MAX_VALUE).forEach { offset ->
            assertThrows(IllegalArgumentException::class.java) { reader.readChunk(fixture.signal, 1, LegacyEndpointField.NAME, offset) }
        }
        assertTrue(fixture.queries.isEmpty())
        listOf(chunkRow(1, length = 1, bytes = byteArrayOf()), chunkRow(1, "null", null, null), chunkRow(1, "integer", null, null)).forEach { row ->
            val current = Fixture(chunkRows = listOf(row))
            assertThrows(IllegalArgumentException::class.java) {
                LegacyEndpointValueChunkReader(current.database).readChunk(current.signal, 1, LegacyEndpointField.NAME, 2)
            }
            current.assertReadOnlyAndClosed()
        }
    }

    @Test
    fun `chunk metadata disagreement and oversized or short payload fail closed`() {
        val valid = chunkRow(1, length = 1, bytes = byteArrayOf(0))
        val cases = listOf(
            valid + mapOf("chunk_rowid" to "1"), valid + mapOf("chunk_rowid" to 2L),
            valid + mapOf("value_type" to "unknown"), valid + mapOf("value_type" to 2L),
            valid + mapOf("value_bytes" to "1"), valid + mapOf("value_bytes" to -1L),
            valid + mapOf("value_bytes" to null), valid + mapOf("value_bounded" to "x"),
            valid + mapOf("value_bounded" to null), valid + mapOf("value_bounded" to byteArrayOf()),
            valid + mapOf("value_bytes" to 513L, "value_bounded" to ByteArray(513)),
            valid + mapOf("value_type" to "integer"), valid + mapOf("value_type" to "null")
        )
        cases.forEach { row ->
            val fixture = Fixture(chunkRows = listOf(row))
            assertThrows(IllegalStateException::class.java) {
                LegacyEndpointValueChunkReader(fixture.database).readChunk(fixture.signal, 1, LegacyEndpointField.NAME)
            }
            fixture.assertReadOnlyAndClosed()
        }
        val duplicate = Fixture(chunkRows = listOf(valid, valid))
        assertThrows(IllegalStateException::class.java) {
            LegacyEndpointValueChunkReader(duplicate.database).readChunk(duplicate.signal, 1, LegacyEndpointField.NAME)
        }
        duplicate.assertReadOnlyAndClosed()
    }

    @Test
    fun `chunk retains raw invalid UTF8 and copies input and output without diagnostic exposure`() {
        val original = "wss://user:password@private.invalid/".toByteArray() + byteArrayOf(0, -128, -1, -30)
        val expected = original.copyOf()
        val chunk = LegacyEndpointValueChunk(1, LegacyEndpointField.ENDPOINT, 0, LegacyEndpointChunkState.AVAILABLE,
            LegacyEndpointStorage.TEXT, original.size.toLong(), original)
        original.fill(42)
        checkNotNull(chunk.bytes).fill(24)
        assertArrayEquals(expected, chunk.bytes)
        assertFalse(chunk.toString().contains("password"))
        assertFalse(chunk.toString().contains("private.invalid"))
        assertFalse(chunk.toString().contains(expected.contentToString()))
        assertEquals(expected.size, chunk.byteCount)
    }

    @Test
    fun `chunk cancellation encoding and SQL errors propagate and close all cursors`() {
        val canceled = IllegalStateException("Synthetic cancellation")
        val signal: CancellationSignal = mock { on { throwIfCanceled() } doAnswer { throw canceled } }
        val untouched = Fixture(signal = signal)
        assertSame(canceled, assertThrows(IllegalStateException::class.java) {
            LegacyEndpointValueChunkReader(untouched.database).readChunk(signal, 1, LegacyEndpointField.NAME)
        })
        assertTrue(untouched.queries.isEmpty())
        val encoding = Fixture(encoding = "UTF-16le")
        assertThrows(IllegalStateException::class.java) {
            LegacyEndpointValueChunkReader(encoding.database).readChunk(encoding.signal, 1, LegacyEndpointField.NAME)
        }
        assertEquals(listOf("PRAGMA encoding"), encoding.queries)
        encoding.assertReadOnlyAndClosed()
        listOf("sqlite_temp_master", "SELECT rowid AS chunk_rowid").forEach { at ->
            val failure = IllegalStateException("Synthetic chunk SQL error")
            val fixture = Fixture(failureAt = at, failure = failure)
            assertSame(failure, assertThrows(IllegalStateException::class.java) {
                LegacyEndpointValueChunkReader(fixture.database).readChunk(fixture.signal, 1, LegacyEndpointField.NAME)
            })
            fixture.assertReadOnlyAndClosed()
        }
    }

    private class Fixture(
        val signal: CancellationSignal = mock(),
        val encoding: String = "UTF-8",
        val present: Boolean = true,
        val pageRows: List<Map<String, Any?>> = emptyList(),
        val chunkRows: List<Map<String, Any?>> = emptyList(),
        val oppositeExists: Boolean = false,
        val failureAt: String? = null,
        val failure: RuntimeException = IllegalStateException("Synthetic query failure")
    ) {
        val queries = mutableListOf<String>()
        val bindings = mutableMapOf<String, List<Long>>()
        val cursors = mutableListOf<Cursor>()
        val database: SupportSQLiteDatabase = mock {
            on { query(any<SupportSQLiteQuery>(), any<CancellationSignal>()) } doAnswer {
                assertSame(signal, it.arguments[1])
                val query = it.arguments[0] as SupportSQLiteQuery
                val longs = mutableListOf<Long>()
                val program: SupportSQLiteProgram = mock {
                    on { bindLong(any(), any()) } doAnswer { binding -> longs += binding.arguments[1] as Long; Unit }
                }
                query.bindTo(program)
                bindings[query.sql] = longs
                answer(query.sql)
            }
        }

        fun assertReadOnlyAndClosed() {
            verify(database, never()).query(any<String>())
            verify(database, never()).query(any<String>(), any<Array<Any?>>())
            verify(database, never()).query(any<SupportSQLiteQuery>())
            verify(database, never()).execSQL(any<String>())
            verify(database, never()).execSQL(any<String>(), any<Array<Any?>>())
            verify(database, never()).beginTransaction()
            verify(database, never()).close()
            cursors.forEach { verify(it).close() }
        }

        private fun answer(sql: String): Cursor {
            queries += sql
            if (failureAt != null && sql.contains(failureAt)) throw failure
            val rows: List<Map<String, Any?>> = when {
                sql == "PRAGMA encoding" -> listOf(mapOf("encoding" to encoding))
                sql.contains("sqlite_temp_master") -> emptyList()
                sql.contains("FROM main.sqlite_master") -> if (present) listOf(mapOf("type" to "table", "name" to "nodes", "tbl_name" to "nodes")) else emptyList()
                sql.startsWith("PRAGMA main.foreign_key_list") || sql.startsWith("PRAGMA main.index_list") -> emptyList()
                sql.startsWith("SELECT type FROM sqlite_master") -> listOf(mapOf("type" to "table"))
                sql.contains("releasedBoundedObjectSql") -> listOf(bounded("releasedBoundedObjectType", "table") + bounded("releasedBoundedObjectSql", DDL))
                sql.contains("pragma_table_info") -> COLUMNS.mapIndexed { index, column ->
                    mapOf("cid" to index.toLong(), "notnull" to 1L, "pk" to if (column == "id") 1L else 0L) +
                        bounded("releasedBoundedColumnName", column) +
                        bounded("releasedBoundedColumnType", if (column in listOf("name", "link")) "TEXT" else "INTEGER") +
                        bounded("releasedBoundedColumnDefault", null)
                }
                sql.contains("pragma_index_list") -> emptyList()
                sql.startsWith("SELECT rowid AS chunk_rowid") -> chunkRows
                sql.startsWith("SELECT rowid AS archive_rowid") -> pageRows
                sql.startsWith("SELECT 1 FROM main.nodes") -> if (oppositeExists) listOf(mapOf("exists" to 1L)) else emptyList()
                else -> error("Unexpected inventory query: $sql")
            }
            return cursor(rows).also { cursors += it }
        }
    }

    private companion object {
        val COLUMNS = listOf("id", "name", "link", "networkType", "isDefault", "isActive")
        const val DDL = "CREATE TABLE nodes(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,link TEXT NOT NULL,networkType INTEGER NOT NULL,isDefault INTEGER NOT NULL,isActive INTEGER NOT NULL)"
        fun bounded(alias: String, value: String?): Map<String, Any?> = mapOf(
            alias to value, "${alias}StorageClass" to if (value == null) "null" else "text",
            "${alias}ByteLength" to value?.toByteArray()?.size?.toLong()
        )
        fun chunkRow(id: Long, type: String = "text", length: Long? = 0, bytes: ByteArray? = byteArrayOf()): Map<String, Any?> =
            mapOf("chunk_rowid" to id, "value_type" to type, "value_bytes" to length, "value_bounded" to bytes)
        fun row(id: Long, name: String = "Saved", endpoint: String = "offline"): Map<String, Any?> =
            mapOf("archive_rowid" to id) +
                listOf("name" to name, "link" to endpoint).flatMap { (column, value) ->
                    listOf("${column}_type" to "text", "${column}_bytes" to value.toByteArray(Charsets.UTF_8).size.toLong(),
                        "${column}_bounded" to value.toByteArray(Charsets.UTF_8))
                }.toMap() + listOf("networkType" to 15L, "isDefault" to 0L, "isActive" to 1L).flatMap { (column, value) ->
                    listOf("${column}_type" to "integer", "${column}_bytes" to value.toString().length.toLong(), "${column}_integer" to value)
                }.toMap()

        fun cursor(rows: List<Map<String, Any?>>): Cursor {
            val names = rows.flatMap { it.keys }.distinct()
            var position = -1
            fun value(index: Int) = rows[position][names[index]]
            return mock {
                on { columnCount } doAnswer { names.size }
                on { moveToFirst() } doAnswer { position = 0; rows.isNotEmpty() }
                on { moveToNext() } doAnswer { ++position < rows.size }
                on { getColumnIndexOrThrow(any()) } doAnswer { invocation ->
                    names.indexOf(invocation.arguments[0] as String).also { check(it >= 0) }
                }
                on { isNull(any()) } doAnswer { value(it.arguments[0] as Int) == null }
                on { getType(any()) } doAnswer { when (value(it.arguments[0] as Int)) {
                    null -> Cursor.FIELD_TYPE_NULL
                    is Long -> Cursor.FIELD_TYPE_INTEGER
                    is ByteArray -> Cursor.FIELD_TYPE_BLOB
                    else -> Cursor.FIELD_TYPE_STRING
                } }
                on { getLong(any()) } doAnswer { value(it.arguments[0] as Int) as Long }
                on { getInt(any()) } doAnswer { (value(it.arguments[0] as Int) as Long).toInt() }
                on { getString(any()) } doAnswer { value(it.arguments[0] as Int) as String? }
                on { getBlob(any()) } doAnswer { value(it.arguments[0] as Int) as ByteArray }
            }
        }
    }
}
