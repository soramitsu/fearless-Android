package jp.co.soramitsu.coredb.recovery

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

/** Exact bytes from one row/revision read. A stale reference never reads a new payload. */
class EndpointValueChunkReader(private val database: SupportSQLiteDatabase) {
    fun readChunk(
        cancellationSignal: CancellationSignal,
        ref: EndpointRecordRef,
        field: EndpointField,
        byteOffset: Long = 0
    ): EndpointValueChunk {
        require(byteOffset in 0 until Long.MAX_VALUE)
        val column = requireNotNull(endpointSourceSpec(ref.source).fields[field]) { "Field does not belong to this source" }
        val session = admitEndpointSource(database, cancellationSignal, ref.source)
            ?: return EndpointValueChunk(ref, field, byteOffset, EndpointChunkState.SOURCE_ABSENT, null, null, null)
        val owned = ref.source == EndpointInventorySource.OWNED
        // Indexed parameters reuse one expected revision within this single SQLite snapshot.
        val gate = if (owned) "typeof(revision)='integer' AND revision=?1" else "1"
        val offsetArg = if (owned) "?2" else "?1"
        val capArg = if (owned) "?3" else "?2"
        val idArg = if (owned) "?4" else "?3"
        val metadata = if (owned) ", $OWNED_ENDPOINT_PROJECTION" else ""
        val query = SimpleSQLiteQuery(
            "SELECT ${session.spec.rowId} AS endpoint_rowid$metadata, " +
                "CASE WHEN $gate THEN typeof($column) END AS value_type, " +
                "CASE WHEN ($gate) AND typeof($column) IN ('text','blob') THEN length(CAST($column AS BLOB)) END AS value_bytes, " +
                "CASE WHEN ($gate) AND typeof($column) IN ('text','blob') THEN " +
                    "CASE WHEN length(CAST($column AS BLOB))=0 THEN X'' ELSE substr(CAST($column AS BLOB),$offsetArg,$capArg) END END AS value_bounded, " +
                "CASE WHEN ($gate) AND typeof($column)='integer' THEN $column END AS value_integer, " +
                "CASE WHEN ($gate) AND typeof($column)='real' THEN $column END AS value_real " +
                "FROM ${session.spec.table} WHERE ${session.spec.rowId}=$idArg LIMIT 1",
            if (owned) arrayOf(ref.revision, byteOffset + 1, MAXIMUM_CHUNK_BYTES, ref.rowId)
            else arrayOf(byteOffset + 1, MAXIMUM_CHUNK_BYTES, ref.rowId)
        )
        val result = session.database.query(query).use { cursor ->
            cancellationSignal.throwIfCanceled()
            if (!cursor.moveToFirst()) {
                EndpointValueChunk(ref, field, byteOffset, EndpointChunkState.ROW_ABSENT, null, null, null)
            } else {
                check(cursor.endpointInteger(cursor.getColumnIndexOrThrow("endpoint_rowid")) == ref.rowId) { "Endpoint row identity differs" }
                val ownership = if (owned) cursor.endpointOwnership() else null
                val value = if (ownership != null && ownership.first != ref.revision) {
                    listOf("value_type", "value_bytes", "value_bounded", "value_integer", "value_real").forEach {
                        check(cursor.isNull(cursor.getColumnIndexOrThrow(it))) { "Stale endpoint exposed payload metadata" }
                    }
                    EndpointValueChunk(ref, field, byteOffset, EndpointChunkState.STALE_REVISION, null, null, null,
                        observedRevision = ownership.first)
                } else {
                    val storage = cursor.endpointStorage("value")
                    val lengthIndex = cursor.getColumnIndexOrThrow("value_bytes")
                    val length = if (cursor.isNull(lengthIndex)) null else cursor.endpointInteger(lengthIndex).also { check(it >= 0) }
                    val byteStorage = storage == EndpointStorage.TEXT || storage == EndpointStorage.BLOB
                    check(byteStorage == (length != null)) { "Endpoint chunk length differs from storage" }
                    if (byteStorage) require(byteOffset <= checkNotNull(length)) { "Endpoint chunk offset exceeds value" }
                    else require(byteOffset == 0L) { "Numeric and NULL values have no byte offsets" }
                    val payloadIndex = cursor.getColumnIndexOrThrow("value_bounded")
                    val bytes = if (cursor.isNull(payloadIndex)) null else {
                        check(cursor.getType(payloadIndex) == Cursor.FIELD_TYPE_BLOB) { "Endpoint chunk payload is unreadable" }
                        cursor.getBlob(payloadIndex).also { check(it.size <= MAXIMUM_CHUNK_BYTES) }
                    }
                    val integerIndex = cursor.getColumnIndexOrThrow("value_integer")
                    val realIndex = cursor.getColumnIndexOrThrow("value_real")
                    check((storage == EndpointStorage.INTEGER) == !cursor.isNull(integerIndex) &&
                        (storage == EndpointStorage.REAL) == !cursor.isNull(realIndex)) { "Endpoint chunk scalar projection differs" }
                    val integer = if (storage == EndpointStorage.INTEGER) cursor.endpointInteger(integerIndex) else null
                    val real = if (storage == EndpointStorage.REAL) {
                        check(cursor.getType(realIndex) == Cursor.FIELD_TYPE_FLOAT); cursor.getDouble(realIndex)
                    } else null
                    EndpointValueChunk(ref, field, byteOffset, when (storage) {
                        EndpointStorage.TEXT, EndpointStorage.BLOB -> EndpointChunkState.AVAILABLE
                        EndpointStorage.NULL -> EndpointChunkState.NULL_VALUE
                        EndpointStorage.INTEGER, EndpointStorage.REAL -> EndpointChunkState.NON_BYTE_STORAGE
                    }, storage, length, bytes, ownership?.first, ownership?.second, integer, real)
                }
                check(!cursor.moveToNext()) { "Endpoint chunk query returned multiple rows" }
                value
            }
        }
        cancellationSignal.throwIfCanceled()
        return result
    }

    companion object { const val MAXIMUM_CHUNK_BYTES = 512 }
}
