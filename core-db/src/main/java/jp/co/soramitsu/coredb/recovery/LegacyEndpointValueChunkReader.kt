package jp.co.soramitsu.coredb.recovery

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Bounded exact-byte inspection under the same archive admission as inventory.
 * Call off the UI thread with a caller-owned cancellation/deadline. Every result
 * describes this read only: revalidate after database/session replacement and
 * never use a row, length or offset as attachment or authorization evidence.
 */
class LegacyEndpointValueChunkReader(private val database: SupportSQLiteDatabase) {
    fun readChunk(
        cancellationSignal: CancellationSignal,
        originalRowId: Long,
        field: LegacyEndpointField,
        byteOffset: Long = 0
    ): LegacyEndpointValueChunk {
        require(byteOffset in 0 until Long.MAX_VALUE) { "Archive chunk offset is invalid" }
        val cancellable = admittedLegacyEndpointArchive(database, cancellationSignal)
            ?: return LegacyEndpointValueChunk(originalRowId, field, byteOffset,
                LegacyEndpointChunkState.ARCHIVE_ABSENT, null, null, null)
        val column = field.column
        val query = SimpleSQLiteQuery(
            "SELECT rowid AS chunk_rowid, typeof($column) AS value_type, " +
                "CASE WHEN typeof($column) IN ('text','blob') THEN length(CAST($column AS BLOB)) ELSE NULL END AS value_bytes, " +
                "CASE WHEN typeof($column) IN ('text','blob') THEN " +
                    "CASE WHEN length(CAST($column AS BLOB)) = 0 THEN X'' " +
                    "ELSE substr(CAST($column AS BLOB),?,?) END ELSE NULL END AS value_bounded " +
                "FROM main.nodes WHERE rowid = ? LIMIT 1",
            arrayOf(byteOffset + 1, MAXIMUM_CHUNK_BYTES, originalRowId)
        )
        val result = cancellable.query(query).use { cursor ->
            cancellationSignal.throwIfCanceled()
            if (!cursor.moveToFirst()) {
                LegacyEndpointValueChunk(originalRowId, field, byteOffset,
                    LegacyEndpointChunkState.ROW_ABSENT, null, null, null)
            } else {
                check(cursor.columnCount == 4) { "Archive chunk projection is unreadable" }
                val rowIndex = cursor.getColumnIndexOrThrow("chunk_rowid")
                check(cursor.getType(rowIndex) == Cursor.FIELD_TYPE_INTEGER && cursor.getLong(rowIndex) == originalRowId) {
                    "Archive chunk row identity disagrees"
                }
                val storage = cursor.legacyEndpointStorage("value")
                val lengthIndex = cursor.getColumnIndexOrThrow("value_bytes")
                val length = if (cursor.isNull(lengthIndex)) null else {
                    check(cursor.getType(lengthIndex) == Cursor.FIELD_TYPE_INTEGER) { "Archive chunk length is unreadable" }
                    cursor.getLong(lengthIndex).also { check(it >= 0) { "Archive chunk length is negative" } }
                }
                val readable = storage == LegacyEndpointStorage.TEXT || storage == LegacyEndpointStorage.BLOB
                if (readable) {
                    checkNotNull(length) { "Archive chunk length is absent" }
                    require(byteOffset <= length) { "Archive chunk offset exceeds current length" }
                } else require(byteOffset == 0L) { "Archive storage has no byte offsets" }
                val payloadIndex = cursor.getColumnIndexOrThrow("value_bounded")
                cancellationSignal.throwIfCanceled()
                val bytes = if (cursor.isNull(payloadIndex)) null else {
                    check(cursor.getType(payloadIndex) == Cursor.FIELD_TYPE_BLOB) { "Archive chunk payload is unreadable" }
                    cursor.getBlob(payloadIndex).also { check(it.size <= MAXIMUM_CHUNK_BYTES) { "Archive chunk exceeds its cap" } }
                }
                val state = when (storage) {
                    LegacyEndpointStorage.TEXT, LegacyEndpointStorage.BLOB -> LegacyEndpointChunkState.AVAILABLE
                    LegacyEndpointStorage.NULL -> LegacyEndpointChunkState.NULL_VALUE
                    LegacyEndpointStorage.INTEGER, LegacyEndpointStorage.REAL -> LegacyEndpointChunkState.UNSUPPORTED_STORAGE
                }
                val chunk = LegacyEndpointValueChunk(originalRowId, field, byteOffset, state, storage, length, bytes)
                check(!cursor.moveToNext()) { "Archive chunk projection returned multiple rows" }
                chunk
            }
        }
        cancellationSignal.throwIfCanceled()
        return result
    }

    companion object {
        const val MAXIMUM_CHUNK_BYTES = 512
    }
}
