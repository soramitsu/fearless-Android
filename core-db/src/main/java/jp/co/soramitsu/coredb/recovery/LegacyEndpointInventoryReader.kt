package jp.co.soramitsu.coredb.recovery

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Read-only inventory of the finite admitted main.nodes archive. Invoke off the
 * UI thread with a caller-owned cancellation/deadline signal. The connection
 * belongs to the caller and is never opened, closed, transacted or mutated here.
 *
 * Keyset cursors are positions in this archive, not mutation authorization or
 * persistent database-generation leases. Restart paging after database/session
 * replacement. Future attachment must independently revalidate the exact source.
 */
class LegacyEndpointInventoryReader(private val database: SupportSQLiteDatabase) {
    fun readPage(
        cancellationSignal: CancellationSignal,
        afterExclusiveRowId: Long? = null,
        beforeExclusiveRowId: Long? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): LegacyEndpointPage {
        require(pageSize in 1..MAXIMUM_PAGE_SIZE)
        require(afterExclusiveRowId == null || beforeExclusiveRowId == null)
        val cancellable = admittedLegacyEndpointArchive(database, cancellationSignal)
            ?: return LegacyEndpointPage(false, emptyList(), null, null)
        val reverse = beforeExclusiveRowId != null
        val boundary = beforeExclusiveRowId ?: afterExclusiveRowId
        val comparison = if (reverse) "<" else ">"
        val where = if (boundary == null) "" else " WHERE rowid $comparison ?"
        val arguments: Array<out Any?> = if (boundary == null) emptyArray() else arrayOf(boundary)
        val direction = if (reverse) "DESC" else "ASC"
        val query = SimpleSQLiteQuery(
            "SELECT rowid AS archive_rowid, ${TEXT_PROJECTION.joinToString()}, ${INTEGER_PROJECTION.joinToString()} " +
                "FROM main.nodes$where ORDER BY rowid $direction LIMIT ${pageSize + 1}", arguments
        )
        val records = ArrayList<LegacyEndpointRecord>(pageSize)
        var hasMore = false
        cancellable.query(query).use { cursor ->
            while (cursor.moveToNext()) {
                cancellationSignal.throwIfCanceled()
                if (records.size == pageSize) { hasMore = true; break }
                val index = cursor.getColumnIndexOrThrow("archive_rowid")
                check(cursor.getType(index) == Cursor.FIELD_TYPE_INTEGER) { "Archive row identity is unreadable" }
                val rowId = cursor.getLong(index)
                check(boundary == null || if (reverse) rowId < boundary else rowId > boundary) { "Archive cursor did not advance" }
                check(records.isEmpty() || if (reverse) rowId < records.last().originalRowId else rowId > records.last().originalRowId) {
                    "Archive rows are not ordered"
                }
                records += LegacyEndpointRecord(
                    rowId, cursor.text("name"), cursor.text("link"), cursor.integer("networkType"),
                    cursor.integer("isDefault"), cursor.integer("isActive")
                )
            }
        }
        cancellationSignal.throwIfCanceled()
        if (records.isEmpty()) return LegacyEndpointPage(true, emptyList(), null, null)
        if (reverse) records.reverse()
        val first = records.first().originalRowId
        val last = records.last().originalRowId
        val oppositeExists = cancellable.query(SimpleSQLiteQuery(
            "SELECT 1 FROM main.nodes WHERE rowid ${if (reverse) ">" else "<"} ? LIMIT 1",
            arrayOf(if (reverse) last else first)
        )).use { it.moveToFirst() }
        cancellationSignal.throwIfCanceled()
        val hasEarlier = if (reverse) hasMore else oppositeExists
        val hasLater = if (reverse) oppositeExists else hasMore
        return LegacyEndpointPage(true, records.toList(), if (hasEarlier) first else null, if (hasLater) last else null)
    }

    private fun Cursor.text(column: String): LegacyEndpointText {
        val storage = legacyEndpointStorage(column)
        val lengthIndex = getColumnIndexOrThrow("${column}_bytes")
        val byteLength = if (isNull(lengthIndex)) null else {
            check(getType(lengthIndex) == Cursor.FIELD_TYPE_INTEGER) { "Archive length metadata is unreadable" }
            getLong(lengthIndex)
        }
        val valueIndex = getColumnIndexOrThrow("${column}_bounded")
        val bytes = if (isNull(valueIndex)) null else {
            check(getType(valueIndex) == Cursor.FIELD_TYPE_BLOB) { "Archive bounded payload has unexpected storage" }
            getBlob(valueIndex).also { check(it.size <= MAXIMUM_TEXT_BYTES) { "Archive bounded payload exceeds its cap" } }
        }
        return decodeLegacyEndpointText(storage, byteLength, bytes, MAXIMUM_TEXT_BYTES)
    }

    private fun Cursor.integer(column: String): LegacyEndpointInteger {
        val storage = legacyEndpointStorage(column)
        val lengthIndex = getColumnIndexOrThrow("${column}_bytes")
        val byteLength = if (isNull(lengthIndex)) null else {
            check(getType(lengthIndex) == Cursor.FIELD_TYPE_INTEGER) { "Archive integer length is unreadable" }
            getLong(lengthIndex).also { check(it >= 0L) { "Archive integer length is negative" } }
        }
        check((storage == LegacyEndpointStorage.NULL) == (byteLength == null)) { "Archive integer length disagrees" }
        val index = getColumnIndexOrThrow("${column}_integer")
        val integer = if (isNull(index)) null else {
            check(getType(index) == Cursor.FIELD_TYPE_INTEGER) { "Archive integer projection is unreadable" }
            getLong(index)
        }
        check((storage == LegacyEndpointStorage.INTEGER) == (integer != null)) { "Archive integer metadata disagrees" }
        return LegacyEndpointInteger(storage, byteLength, integer)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 25
        const val MAXIMUM_PAGE_SIZE = 50
        const val MAXIMUM_TEXT_BYTES = 2048
        private val TEXT_PROJECTION = listOf("name", "link").flatMap { column ->
            listOf("typeof($column) AS ${column}_type", "length(CAST($column AS BLOB)) AS ${column}_bytes",
                "CASE WHEN typeof($column) = 'text' AND length(CAST($column AS BLOB)) <= $MAXIMUM_TEXT_BYTES " +
                    "THEN CAST($column AS BLOB) ELSE NULL END AS ${column}_bounded")
        }
        private val INTEGER_PROJECTION = listOf("networkType", "isDefault", "isActive").flatMap { column ->
            listOf("typeof($column) AS ${column}_type", "length(CAST($column AS BLOB)) AS ${column}_bytes",
                "CASE WHEN typeof($column) = 'integer' THEN $column ELSE NULL END AS ${column}_integer")
        }
    }
}

/** Preserve the distinction between absent, unsupported and truncated data; never replacement-decode bytes. */
internal fun decodeLegacyEndpointText(
    storage: LegacyEndpointStorage,
    byteLength: Long?,
    bytes: ByteArray?,
    maximumBytes: Int
): LegacyEndpointText {
    require(maximumBytes in 1..LegacyEndpointInventoryReader.MAXIMUM_TEXT_BYTES)
    check(byteLength == null || byteLength >= 0) { "Archive byte length is negative" }
    check((storage == LegacyEndpointStorage.NULL) == (byteLength == null)) { "Archive byte length disagrees with storage" }
    val state = when {
        storage == LegacyEndpointStorage.NULL -> LegacyEndpointTextState.NULL
        storage != LegacyEndpointStorage.TEXT -> LegacyEndpointTextState.UNSUPPORTED_STORAGE
        checkNotNull(byteLength) > maximumBytes -> LegacyEndpointTextState.OVERSIZED
        else -> LegacyEndpointTextState.AVAILABLE
    }
    if (state != LegacyEndpointTextState.AVAILABLE) {
        check(bytes == null) { "Archive projected an unbounded or unsupported value" }
        return LegacyEndpointText(storage, byteLength, state, null)
    }
    check(bytes != null && bytes.size <= maximumBytes && bytes.size.toLong() == byteLength) {
        "Archive bounded value disagrees with its byte length"
    }
    val text = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        return LegacyEndpointText(storage, byteLength, LegacyEndpointTextState.INVALID_UTF8, null)
    }
    return LegacyEndpointText(storage, byteLength, LegacyEndpointTextState.AVAILABLE, text)
}
