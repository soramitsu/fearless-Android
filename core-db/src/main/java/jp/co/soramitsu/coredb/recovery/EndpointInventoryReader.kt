package jp.co.soramitsu.coredb.recovery

import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

/** Read-only fixed-source inventory. Invoke off the UI thread with a caller-owned deadline. */
class EndpointInventoryReader(private val database: SupportSQLiteDatabase) {
    fun readPage(
        cancellationSignal: CancellationSignal,
        source: EndpointInventorySource,
        afterExclusiveRowId: Long? = null,
        beforeExclusiveRowId: Long? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): EndpointPage {
        require(pageSize in 1..MAXIMUM_PAGE_SIZE)
        require(afterExclusiveRowId == null || beforeExclusiveRowId == null)
        val session = admitEndpointSource(database, cancellationSignal, source)
            ?: return EndpointPage(source, false, null, emptyList(), null, null)
        val spec = session.spec
        val reverse = beforeExclusiveRowId != null
        val boundary = beforeExclusiveRowId ?: afterExclusiveRowId
        val where = if (boundary == null) "" else " WHERE ${spec.rowId} ${if (reverse) "<" else ">"} ?"
        val args: Array<out Any?> = if (boundary == null) emptyArray() else arrayOf(boundary)
        val fields = spec.fields.entries.toList()
        val projection = fields.mapIndexed { index, entry -> endpointPageProjection(entry.value, "field$index") }.joinToString()
        val ownership = if (source == EndpointInventorySource.OWNED) ", $OWNED_ENDPOINT_PROJECTION" else ""
        val query = SimpleSQLiteQuery("SELECT ${spec.rowId} AS endpoint_rowid, $projection$ownership FROM ${spec.table}$where " +
            "ORDER BY ${spec.rowId} ${if (reverse) "DESC" else "ASC"} LIMIT ${pageSize + 1}", args)
        val rows = ArrayList<EndpointRecord>(pageSize)
        var more = false
        session.database.query(query).use { cursor ->
            while (cursor.moveToNext()) {
                cancellationSignal.throwIfCanceled()
                if (rows.size == pageSize) { more = true; break }
                val id = cursor.endpointInteger(cursor.getColumnIndexOrThrow("endpoint_rowid"))
                check(boundary == null || if (reverse) id < boundary else id > boundary) { "Endpoint cursor did not advance" }
                check(rows.isEmpty() || if (reverse) id < rows.last().ref.rowId else id > rows.last().ref.rowId) {
                    "Endpoint rows are not ordered"
                }
                val owned = if (source == EndpointInventorySource.OWNED) cursor.endpointOwnership() else null
                val values = fields.mapIndexed { index, entry -> entry.key to cursor.endpointPageValue("field$index") }.toMap()
                rows += EndpointRecord(EndpointRecordRef(source, id, owned?.first), values, owned?.second)
            }
        }
        cancellationSignal.throwIfCanceled()
        if (rows.isEmpty()) return EndpointPage(source, true, session.provenance, emptyList(), null, null)
        if (reverse) rows.reverse()
        val first = rows.first().ref.rowId
        val last = rows.last().ref.rowId
        val opposite = session.database.query(SimpleSQLiteQuery(
            "SELECT 1 FROM ${spec.table} WHERE ${spec.rowId} ${if (reverse) ">" else "<"} ? LIMIT 1",
            arrayOf(if (reverse) last else first)
        )).use { it.moveToFirst() }
        cancellationSignal.throwIfCanceled()
        return EndpointPage(source, true, session.provenance, rows.toList(),
            if (if (reverse) more else opposite) first else null,
            if (if (reverse) opposite else more) last else null)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 25
        const val MAXIMUM_PAGE_SIZE = 50
        const val MAXIMUM_TEXT_BYTES = 2048
    }
}
