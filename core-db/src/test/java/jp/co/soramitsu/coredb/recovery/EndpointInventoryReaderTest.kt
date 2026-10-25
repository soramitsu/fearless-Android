package jp.co.soramitsu.coredb.recovery

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class EndpointInventoryReaderTest {
    @Test fun fixedSourcesUseDistinctQualifiedTablesAndExactFieldSets() {
        assertEquals(5, EndpointInventorySource.values().size)
        assertEquals(5, EndpointInventorySource.values().map { endpointSourceSpec(it).table }.toSet().size)
        EndpointInventorySource.values().forEach { source ->
            val spec = endpointSourceSpec(source)
            assertTrue(spec.table.startsWith("main."))
            assertEquals(if (source == EndpointInventorySource.OWNED) 3 else 5, spec.fields.size)
            assertEquals(source == EndpointInventorySource.LEGACY, EndpointField.NETWORK_TYPE in spec.fields)
            assertEquals(source != EndpointInventorySource.LEGACY, EndpointField.CHAIN_REFERENCE in spec.fields)
        }
    }

    @Test fun identityRequiresOnlyOwnedPositiveRevisionAndPreservesSignedIds() {
        EndpointInventorySource.values().forEach { source ->
            listOf(Long.MIN_VALUE, 0L, Long.MAX_VALUE).forEach { id ->
                val revision = if (source == EndpointInventorySource.OWNED) Long.MAX_VALUE else null
                assertEquals(id, EndpointRecordRef(source, id, revision).rowId)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { EndpointRecordRef(EndpointInventorySource.OWNED, 1) }
        listOf(0L, -1L).forEach { revision ->
            assertThrows(IllegalArgumentException::class.java) { EndpointRecordRef(EndpointInventorySource.OWNED, 1, revision) }
        }
        assertThrows(IllegalArgumentException::class.java) { EndpointRecordRef(EndpointInventorySource.CURRENT, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { EndpointOwnership(true, true, null) }
        assertThrows(IllegalArgumentException::class.java) { EndpointSourceProvenance(39, -1) }
    }

    @Test fun valuesRetainNumericTypesAndStrictBoundedTextWithoutDiagnosticPayload() {
        val secret = "wss://user:secret@offline.invalid/雪\u0000"
        val text = EndpointValue.Text(secret.toByteArray().size.toLong(), EndpointTextState.AVAILABLE, secret)
        assertEquals(secret, text.value)
        assertEquals(EndpointStorage.INTEGER, EndpointValue.Integer(Long.MIN_VALUE).storage)
        assertEquals(1.25, EndpointValue.Real(1.25).value, 0.0)
        assertEquals(EndpointStorage.NULL, EndpointValue.Null.storage)
        assertEquals(EndpointStorage.BLOB, EndpointValue.Blob(Long.MAX_VALUE).storage)
        assertFalse(text.toString().contains("secret"))
        assertThrows(IllegalArgumentException::class.java) { EndpointValue.Text(2049, EndpointTextState.AVAILABLE, "a".repeat(2049)) }
        assertThrows(IllegalArgumentException::class.java) { EndpointValue.Text(2, EndpointTextState.AVAILABLE, "雪") }
        assertThrows(IllegalArgumentException::class.java) { EndpointValue.Text(2048, EndpointTextState.OVERSIZED, null) }
        assertThrows(IllegalArgumentException::class.java) { EndpointValue.Text(0, EndpointTextState.INVALID_UTF8, null) }
        assertThrows(IllegalArgumentException::class.java) { EndpointValue.Blob(-1) }
    }

    @Test fun recordsPagesAndChunksOmitPayloadAndRejectSourceMismatch() {
        val value = EndpointValue.Text(6, EndpointTextState.AVAILABLE, "secret")
        val fields = endpointSourceSpec(EndpointInventorySource.OWNED).fields.keys.associateWith { value }
        val record = EndpointRecord(EndpointRecordRef(EndpointInventorySource.OWNED, -7, 1), fields, EndpointOwnership(false, true, -3))
        val page = EndpointPage(EndpointInventorySource.OWNED, true, null, listOf(record), null, null)
        val chunk = EndpointValueChunk(record.ref, EndpointField.NAME, 0, EndpointChunkState.AVAILABLE,
            EndpointStorage.TEXT, 6, "secret".toByteArray(), 1, record.ownership)
        listOf(record.toString(), page.toString(), chunk.toString()).forEach { assertFalse(it.contains("secret")) }
        assertThrows(IllegalArgumentException::class.java) { EndpointRecord(record.ref, emptyMap(), record.ownership) }
        assertThrows(IllegalArgumentException::class.java) { EndpointPage(EndpointInventorySource.CURRENT, true,
            EndpointSourceProvenance(81, 28), listOf(record), null, null) }
        assertThrows(IllegalArgumentException::class.java) { EndpointPage(EndpointInventorySource.CURRENT, false,
            EndpointSourceProvenance(81, 28), emptyList(), null, null) }
        assertThrows(IllegalArgumentException::class.java) { EndpointPage(EndpointInventorySource.CURRENT, true,
            null, emptyList(), null, null) }
    }

    @Test fun bytePartsCopyDefensivelyAndComputeBoundedOffsetsWithoutOverflow() {
        val ref = EndpointRecordRef(EndpointInventorySource.LEGACY, Long.MIN_VALUE)
        val payload = ByteArray(512) { 7 }
        val chunk = EndpointValueChunk(ref, EndpointField.NAME, 512, EndpointChunkState.AVAILABLE,
            EndpointStorage.BLOB, 1025, payload)
        payload[0] = 8
        assertEquals(7.toByte(), checkNotNull(chunk.bytes)[0])
        checkNotNull(chunk.bytes)[0] = 9
        assertEquals(7.toByte(), checkNotNull(chunk.bytes)[0])
        assertEquals(1024L, chunk.nextByteOffset)
        assertEquals(0L, chunk.previousByteOffset)
        val tail = EndpointValueChunk(ref, EndpointField.NAME, Long.MAX_VALUE - 1, EndpointChunkState.AVAILABLE,
            EndpointStorage.TEXT, Long.MAX_VALUE, byteArrayOf(1))
        assertNull(tail.nextByteOffset)
        assertEquals(Long.MAX_VALUE - 513, tail.previousByteOffset)
        assertThrows(IllegalArgumentException::class.java) { EndpointValueChunk(ref, EndpointField.NAME, 0,
            EndpointChunkState.AVAILABLE, EndpointStorage.TEXT, 513, ByteArray(513)) }
    }

    @Test fun staleAndNumericChunkModelsCannotCarryBytesOrInventNewReferences() {
        val ref = EndpointRecordRef(EndpointInventorySource.OWNED, 1, 2)
        val stale = EndpointValueChunk(ref, EndpointField.ENDPOINT, 512, EndpointChunkState.STALE_REVISION,
            null, null, null, observedRevision = 3)
        assertEquals(ref, stale.ref); assertNull(stale.bytes); assertNull(stale.nextByteOffset)
        assertThrows(IllegalArgumentException::class.java) { EndpointValueChunk(ref, EndpointField.NAME, 0,
            EndpointChunkState.STALE_REVISION, EndpointStorage.TEXT, 1, byteArrayOf(1), 3) }
        assertThrows(IllegalArgumentException::class.java) { EndpointValueChunk(ref, EndpointField.NAME, 0,
            EndpointChunkState.STALE_REVISION, null, null, null, 2) }
        val raw = EndpointRecordRef(EndpointInventorySource.LEGACY, 1)
        val numeric = EndpointValueChunk(raw, EndpointField.NETWORK_TYPE, 0, EndpointChunkState.NON_BYTE_STORAGE,
            EndpointStorage.INTEGER, null, null, integerValue = Long.MIN_VALUE)
        assertEquals(Long.MIN_VALUE, numeric.integerValue); assertNull(numeric.bytes)
    }

    @Test fun pageDecoderKeepsFiveStorageClassesAndMalformedUtf8Distinct() {
        val vectors = listOf(
            pageValue("null") to EndpointValue.Null,
            pageValue("integer", integer = Long.MIN_VALUE) to EndpointValue.Integer(Long.MIN_VALUE),
            pageValue("real", real = 1.25) to EndpointValue.Real(1.25),
            pageValue("blob", length = 4096) to EndpointValue.Blob(4096),
            pageValue("text", length = 0, bytes = byteArrayOf()) to EndpointValue.Text(0, EndpointTextState.AVAILABLE, ""),
            pageValue("text", length = 1, bytes = byteArrayOf(-128)) to EndpointValue.Text(1, EndpointTextState.INVALID_UTF8, null),
            pageValue("text", length = 2049) to EndpointValue.Text(2049, EndpointTextState.OVERSIZED, null)
        )
        vectors.forEach { (row, expected) -> cursor(listOf(row)).use { assertTrue(it.moveToFirst()); assertEquals(expected, it.endpointPageValue("v")) } }
    }

    @Test fun pageDecoderRejectsPayloadLengthTypeAndScalarInconsistency() {
        listOf(
            pageValue("text", length = 2, bytes = byteArrayOf(1)),
            pageValue("text", length = 2049, bytes = byteArrayOf(1)),
            pageValue("blob", length = 1, bytes = byteArrayOf(1)),
            pageValue("integer"),
            pageValue("null", integer = 1),
            pageValue("real", real = 1.0).plus("v_real" to 1L)
        ).forEach { row -> cursor(listOf(row)).use { c -> assertTrue(c.moveToFirst());
            assertThrows(IllegalStateException::class.java) { c.endpointPageValue("v") } } }
    }

    @Test fun ownedMetadataReadsMaximumRevisionButRejectsMalformedScalars() {
        cursor(listOf(owned(Long.MAX_VALUE, 0, 1, Long.MIN_VALUE))).use {
            assertTrue(it.moveToFirst()); assertEquals(Long.MAX_VALUE to EndpointOwnership(false, true, Long.MIN_VALUE), it.endpointOwnership())
        }
        listOf(owned(0), owned(1, 1, 1), owned(1, 2), owned(1).plus("owned_revision" to "malformed"),
            owned(1).plus("owned_origin_type" to "text")).forEach { row -> cursor(listOf(row)).use { c ->
            assertTrue(c.moveToFirst()); assertThrows(IllegalStateException::class.java) { c.endpointOwnership() }
        } }
    }

    @Test fun invalidPageAndChunkArgumentsNeverTouchDatabase() {
        val db: SupportSQLiteDatabase = mock(); val signal: CancellationSignal = mock()
        listOf(0, 51).forEach { size -> assertThrows(IllegalArgumentException::class.java) {
            EndpointInventoryReader(db).readPage(signal, EndpointInventorySource.OWNED, pageSize = size) } }
        assertThrows(IllegalArgumentException::class.java) { EndpointInventoryReader(db).readPage(signal,
            EndpointInventorySource.OWNED, 1, 2) }
        assertThrows(IllegalArgumentException::class.java) { EndpointValueChunkReader(db).readChunk(signal,
            EndpointRecordRef(EndpointInventorySource.OWNED, 1, 1), EndpointField.NETWORK_TYPE) }
        assertThrows(IllegalArgumentException::class.java) { EndpointValueChunkReader(db).readChunk(signal,
            EndpointRecordRef(EndpointInventorySource.LEGACY, 1), EndpointField.NAME, Long.MAX_VALUE) }
        verifyNoInteractions(db)
    }

    @Test fun publicOwnedPageReadsBoundedValuesAndMetadataUnderOneSignal() {
        val row = mapOf("endpoint_rowid" to -7L) + owned(Long.MAX_VALUE, 0, 1, Long.MIN_VALUE) +
            listOf("Saved 雪", "wss://offline.invalid", "raw-chain").mapIndexed { i, s ->
                pageValue("text", s.toByteArray().size.toLong(), s.toByteArray(), prefix = "field$i")
            }.fold(emptyMap<String, Any?>()) { acc, value -> acc + value }
        val f = Fixture(pageRows = listOf(row))
        val page = EndpointInventoryReader(f.database).readPage(f.signal, EndpointInventorySource.OWNED)
        assertEquals(EndpointRecordRef(EndpointInventorySource.OWNED, -7, Long.MAX_VALUE), page.records.single().ref)
        assertEquals("raw-chain", (page.records.single().values.getValue(EndpointField.CHAIN_REFERENCE) as EndpointValue.Text).value)
        assertEquals(EndpointOwnership(false, true, Long.MIN_VALUE), page.records.single().ownership)
        assertNull(page.provenance); f.assertClosedAndReadOnly()
    }

    @Test fun liveChunkBindsOneRevisionAndRejectsStaleWithoutAnySecondRead() {
        val ref = EndpointRecordRef(EndpointInventorySource.OWNED, -7, 3)
        val f = Fixture(chunkRows = listOf(mapOf("endpoint_rowid" to -7L) + owned(4) + chunkValue(null)))
        val part = EndpointValueChunkReader(f.database).readChunk(f.signal, ref, EndpointField.ENDPOINT, 512)
        assertEquals(EndpointChunkState.STALE_REVISION, part.state); assertEquals(ref, part.ref)
        assertEquals(4L, part.observedRevision); assertNull(part.bytes); assertNull(part.ownership)
        val selects = f.queries.filter { it.startsWith("SELECT id AS endpoint_rowid") }; assertEquals(1, selects.size)
        assertTrue(selects.single().contains("revision=?1"))
        assertEquals(listOf(3L, 513L, 512L, -7L), f.bindings.getValue(selects.single()))
        f.assertClosedAndReadOnly()
    }

    @Test fun matchingLiveChunkReturnsExactPayloadAndSameQueryOwnership() {
        val ref = EndpointRecordRef(EndpointInventorySource.OWNED, 1, Long.MAX_VALUE)
        val f = Fixture(chunkRows = listOf(mapOf("endpoint_rowid" to 1L) + owned(Long.MAX_VALUE, 0, 1) +
            chunkValue("text", 3, byteArrayOf(0, -128, 2))))
        val part = EndpointValueChunkReader(f.database).readChunk(f.signal, ref, EndpointField.NAME)
        assertEquals(EndpointChunkState.AVAILABLE, part.state)
        assertArrayEquals(byteArrayOf(0, -128, 2), part.bytes)
        assertEquals(EndpointOwnership(false, true, null), part.ownership)
        assertEquals(Long.MAX_VALUE, part.observedRevision); f.assertClosedAndReadOnly()
    }

    @Test fun sourceAbsenceAndMissingRowsDoNotPretendToBeStale() {
        val absent = Fixture(present = false)
        assertFalse(EndpointInventoryReader(absent.database).readPage(absent.signal, EndpointInventorySource.OWNED).present)
        val ref = EndpointRecordRef(EndpointInventorySource.OWNED, 1, 1)
        assertEquals(EndpointChunkState.SOURCE_ABSENT,
            EndpointValueChunkReader(absent.database).readChunk(absent.signal, ref, EndpointField.NAME).state)
        val empty = Fixture()
        assertEquals(EndpointChunkState.ROW_ABSENT,
            EndpointValueChunkReader(empty.database).readChunk(empty.signal, ref, EndpointField.NAME).state)
        assertTrue(EndpointInventoryReader(empty.database).readPage(empty.signal, EndpointInventorySource.OWNED).present)
    }

    @Test fun ownedSchemaAndQueryFailuresPropagateWithoutFabricatingAbsence() {
        val failure = IllegalStateException("owned query failure")
        val f = Fixture(failure = failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            EndpointInventoryReader(f.database).readPage(f.signal, EndpointInventorySource.OWNED) })
        val partial = Fixture(partial = true)
        assertThrows(IllegalStateException::class.java) {
            EndpointInventoryReader(partial.database).readPage(partial.signal, EndpointInventorySource.OWNED) }
    }

    private class Fixture(
        val signal: CancellationSignal = mock(),
        val pageRows: List<Map<String, Any?>> = emptyList(),
        val chunkRows: List<Map<String, Any?>> = emptyList(),
        val present: Boolean = true,
        val partial: Boolean = false,
        val failure: RuntimeException? = null
    ) {
        val queries = mutableListOf<String>(); val bindings = mutableMapOf<String, List<Long>>()
        val cursors = mutableListOf<Cursor>()
        val database: SupportSQLiteDatabase = mock {
            on { query(any<SupportSQLiteQuery>(), any<CancellationSignal>()) } doAnswer {
                assertSame(signal, it.arguments[1]); val query = it.arguments[0] as SupportSQLiteQuery
                queries += query.sql
                val longs = mutableListOf<Long>()
                val program: SupportSQLiteProgram = mock { on { bindLong(any(), any()) } doAnswer { b -> longs += b.arguments[1] as Long; Unit } }
                query.bindTo(program); bindings[query.sql] = longs
                if (failure != null) throw failure
                val rows = when {
                    query.sql == "PRAGMA encoding" -> listOf(mapOf("encoding" to "UTF-8"))
                    query.sql.contains("FROM temp.sqlite_master") -> emptyList()
                    query.sql.contains("FROM main.sqlite_master") -> if (!present) emptyList() else listOf(
                        schema("table", "custom_endpoint_records", OWNED_ENDPOINT_CREATE),
                        schema("index", "index_custom_endpoint_records_chainId_url", OWNED_ENDPOINT_INDEX_CREATE)
                    ).let { if (partial) it.take(1) else it }
                    query.sql.contains("AS value_bounded") -> chunkRows
                    query.sql.startsWith("SELECT id AS endpoint_rowid") -> pageRows
                    query.sql.startsWith("SELECT 1 FROM main.custom_endpoint_records") -> emptyList()
                    else -> error("Unexpected endpoint inventory query")
                }
                cursor(rows).also { c -> cursors += c }
            }
        }
        fun assertClosedAndReadOnly() {
            verify(database, never()).query(any<SupportSQLiteQuery>())
            verify(database, never()).execSQL(any<String>())
            verify(database, never()).beginTransaction()
            verify(database, never()).close()
            cursors.forEach { verify(it).close() }
        }
    }

    private companion object {
        fun owned(revision: Long, attached: Long = 0, deleted: Long = 0, origin: Long? = null): Map<String, Any?> = mapOf(
            "owned_revision" to revision, "owned_attached" to attached, "owned_deleted" to deleted,
            "owned_origin_type" to if (origin == null) "null" else "integer", "owned_origin" to origin)
        fun pageValue(type: String, length: Long? = null, bytes: ByteArray? = null, integer: Long? = null,
            real: Double? = null, prefix: String = "v"): Map<String, Any?> = mapOf("${prefix}_type" to type,
            "${prefix}_bytes" to length, "${prefix}_text" to bytes, "${prefix}_integer" to integer, "${prefix}_real" to real)
        fun chunkValue(type: String?, length: Long? = null, bytes: ByteArray? = null): Map<String, Any?> = mapOf(
            "value_type" to type, "value_bytes" to length, "value_bounded" to bytes, "value_integer" to null, "value_real" to null)
        fun schema(type: String, name: String, sql: String): Map<String, Any?> = linkedMapOf(
            "type" to type.toByteArray(), "name" to name.toByteArray(), "owner" to "custom_endpoint_records".toByteArray(), "sql" to sql.toByteArray())
        fun cursor(rows: List<Map<String, Any?>>): Cursor {
            val names = rows.flatMap { it.keys }.distinct(); var position = -1
            fun value(index: Int) = rows[position][names[index]]
            return mock {
                on { columnCount } doAnswer { names.size }
                on { moveToFirst() } doAnswer { position = 0; rows.isNotEmpty() }
                on { moveToNext() } doAnswer { ++position < rows.size }
                on { getColumnIndexOrThrow(any()) } doAnswer { names.indexOf(it.arguments[0] as String).also { i -> check(i >= 0) } }
                on { isNull(any()) } doAnswer { value(it.arguments[0] as Int) == null }
                on { getType(any()) } doAnswer { when(value(it.arguments[0] as Int)) {
                    null -> Cursor.FIELD_TYPE_NULL
                    is Long -> Cursor.FIELD_TYPE_INTEGER
                    is Double -> Cursor.FIELD_TYPE_FLOAT
                    is ByteArray -> Cursor.FIELD_TYPE_BLOB
                    else -> Cursor.FIELD_TYPE_STRING
                } }
                on { getLong(any()) } doAnswer { value(it.arguments[0] as Int) as Long }
                on { getDouble(any()) } doAnswer { value(it.arguments[0] as Int) as Double }
                on { getString(any()) } doAnswer { value(it.arguments[0] as Int) as String? }
                on { getBlob(any()) } doAnswer { value(it.arguments[0] as Int) as ByteArray }
            }
        }
    }
}
