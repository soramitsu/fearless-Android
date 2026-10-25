package jp.co.soramitsu.coredb.recovery.decision

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import java.nio.ByteBuffer
import java.security.MessageDigest
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.EndpointStorage
import jp.co.soramitsu.coredb.recovery.OWNED_ENDPOINT_PROJECTION
import jp.co.soramitsu.coredb.recovery.admitEndpointSource
import jp.co.soramitsu.coredb.recovery.endpointInteger
import jp.co.soramitsu.coredb.recovery.endpointOwnership
import jp.co.soramitsu.coredb.recovery.endpointStorage

/** Versioned length framing; INTEGER/REAL encode observed typed values, never purported input bytes. */
internal class EndpointDecisionDigest {
    private val digest = MessageDigest.getInstance("SHA-256")

    fun integer(value: Long) { digest.update(ByteBuffer.allocate(8).putLong(value).array()) }
    fun bytes(value: ByteArray) { digest.update(value) }
    fun text(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        integer(encoded.size.toLong())
        bytes(encoded)
    }
    fun finish(): String = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** Internal streaming source binding. Public 512-byte inspection behavior is unchanged. */
internal object EndpointDecisionFingerprint {
    private data class Cell(val storage: EndpointStorage, val length: Long?, val integer: Long?, val realBits: Long?)

    fun prepare(db: SupportSQLiteDatabase, signal: CancellationSignal, ref: EndpointRecordRef): EndpointDecisionSourceBinding {
        check(db.inTransaction()) { "Endpoint source fingerprint requires a transaction" }
        val session = checkNotNull(admitEndpointSource(db, signal, ref.source)) { "Endpoint decision source is absent" }
        val owned = ref.source == EndpointInventorySource.OWNED
        val gate = if (owned) "typeof(revision)='integer' AND revision=?1" else "1"
        val rowArg = if (owned) "?2" else "?1"
        val args = if (owned) arrayOf<Any?>(ref.revision, ref.rowId) else arrayOf<Any?>(ref.rowId)
        val columns = session.spec.fields.values.toList()
        val projection = columns.mapIndexed { index, column ->
            listOf(
                "CASE WHEN $gate THEN typeof($column) END AS cell${index}_type",
                "CASE WHEN ($gate) AND typeof($column) IN ('text','blob') THEN length(CAST($column AS BLOB)) END AS cell${index}_length",
                "CASE WHEN ($gate) AND typeof($column)='integer' THEN $column END AS cell${index}_integer",
                "CASE WHEN ($gate) AND typeof($column)='real' THEN $column END AS cell${index}_real"
            ).joinToString()
        }.joinToString()
        val hash = EndpointDecisionDigest()
        hash.text("endpoint-decision-source-v1")
        hash.text(ref.source.name)
        hash.integer(ref.rowId)
        hash.integer(ref.revision ?: 0)
        hash.integer(if (session.provenance == null) 0 else 1)
        session.provenance?.let {
            hash.integer(it.sourceEdgeVersion.toLong())
            hash.integer(it.originalUserVersion.toLong())
        }
        val cells = session.database.query(SimpleSQLiteQuery(
            "SELECT ${session.spec.rowId} AS source_id" + (if (owned) ",$OWNED_ENDPOINT_PROJECTION" else "") +
                ",$projection FROM ${session.spec.table} WHERE ${session.spec.rowId}=$rowArg LIMIT 1", args
        )).use { cursor ->
            signal.throwIfCanceled()
            check(cursor.moveToFirst()) { "Endpoint decision source row is absent" }
            check(cursor.endpointInteger(cursor.getColumnIndexOrThrow("source_id")) == ref.rowId)
            if (owned) {
                val (revision, ownership) = cursor.endpointOwnership()
                check(revision == ref.revision) { "Endpoint decision source revision is stale" }
                hash.integer(if (ownership.attached) 1 else 0)
                hash.integer(if (ownership.deleted) 1 else 0)
                hash.integer(if (ownership.originalSnapshotRowId == null) 0 else 1)
                ownership.originalSnapshotRowId?.let(hash::integer)
            }
            var remaining = EndpointDecisionLimits.FINGERPRINT_BYTES
            val metadata = columns.indices.map { index ->
                val storage = cursor.endpointStorage("cell$index")
                val lengthIndex = cursor.getColumnIndexOrThrow("cell${index}_length")
                val integerIndex = cursor.getColumnIndexOrThrow("cell${index}_integer")
                val realIndex = cursor.getColumnIndexOrThrow("cell${index}_real")
                val byteStorage = storage == EndpointStorage.TEXT || storage == EndpointStorage.BLOB
                val length = if (byteStorage) cursor.endpointInteger(lengthIndex).also {
                    check(it in 0..remaining) { "Endpoint source fingerprint exceeds its byte budget" }
                    remaining -= it
                } else { check(cursor.isNull(lengthIndex)); null }
                check((storage == EndpointStorage.INTEGER) == !cursor.isNull(integerIndex))
                check((storage == EndpointStorage.REAL) == !cursor.isNull(realIndex))
                val integer = if (storage == EndpointStorage.INTEGER) cursor.endpointInteger(integerIndex) else null
                val real = if (storage == EndpointStorage.REAL) {
                    check(cursor.getType(realIndex) == Cursor.FIELD_TYPE_FLOAT)
                    java.lang.Double.doubleToRawLongBits(cursor.getDouble(realIndex))
                } else null
                Cell(storage, length, integer, real)
            }
            check(!cursor.moveToNext())
            metadata
        }
        // Every cell length was admitted before any source TEXT/BLOB payload read.
        columns.forEachIndexed { index, column ->
            val cell = cells[index]
            hash.text(session.spec.fields.keys.elementAt(index).name)
            hash.text(cell.storage.name)
            when (cell.storage) {
                EndpointStorage.NULL -> Unit
                EndpointStorage.INTEGER -> hash.integer(checkNotNull(cell.integer))
                EndpointStorage.REAL -> hash.integer(checkNotNull(cell.realBits))
                EndpointStorage.TEXT, EndpointStorage.BLOB -> {
                    val length = checkNotNull(cell.length)
                    hash.integer(length)
                    var offset = 0L
                    while (offset < length) {
                        signal.throwIfCanceled()
                        val count = minOf(EndpointDecisionLimits.INTERNAL_CHUNK_BYTES.toLong(), length - offset).toInt()
                        val chunkArgs = if (owned) arrayOf<Any?>(ref.revision, offset + 1, count, ref.rowId)
                            else arrayOf<Any?>(offset + 1, count, ref.rowId)
                        val offsetArg = if (owned) "?2" else "?1"
                        val countArg = if (owned) "?3" else "?2"
                        val idArg = if (owned) "?4" else "?3"
                        session.database.query(SimpleSQLiteQuery(
                            "SELECT CASE WHEN ($gate) AND typeof($column)='${cell.storage.name.lowercase()}' " +
                                "THEN substr(CAST($column AS BLOB),$offsetArg,$countArg) END " +
                                "FROM ${session.spec.table} WHERE ${session.spec.rowId}=$idArg LIMIT 1", chunkArgs
                        )).use { cursor ->
                            signal.throwIfCanceled()
                            check(cursor.columnCount == 1 && cursor.moveToFirst() && cursor.getType(0) == Cursor.FIELD_TYPE_BLOB) {
                                "Endpoint source changed during fingerprint read"
                            }
                            val chunk = cursor.getBlob(0)
                            check(chunk.size == count && !cursor.moveToNext()) { "Endpoint fingerprint chunk differs" }
                            hash.bytes(chunk)
                        }
                        offset += count
                    }
                }
            }
        }
        signal.throwIfCanceled()
        return EndpointDecisionSourceBinding(ref, hash.finish())
    }
}
