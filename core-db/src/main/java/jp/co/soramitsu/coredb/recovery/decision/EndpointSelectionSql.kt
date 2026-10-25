package jp.co.soramitsu.coredb.recovery.decision

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.coredb.recovery.endpointInteger

/** Live selection identity; CURRENT recovery provenance is a separate immutable archive. */
internal object EndpointSelectionSql {
    data class Selected(val rowId: Long, val fingerprint: String)

    fun targetRowId(
        db: SupportSQLiteDatabase,
        signal: CancellationSignal,
        decision: EndpointRecoveryDecision
    ): Long = db.query(
            SimpleSQLiteQuery(
            "SELECT rowid FROM main.chain_nodes WHERE chainId=? AND url=? LIMIT 2",
            arrayOf<Any?>(decision.target.chainId, decision.candidate.url)
        ),
            signal
        ).use {
            signal.throwIfCanceled()
            check(it.columnCount == 1 && it.moveToFirst()) { "Endpoint selection projection is absent" }
            it.endpointInteger(0).also { _ -> check(!it.moveToNext()) { "Endpoint selection projection is ambiguous" } }
        }

    /** No opaque endpoint value enters a CursorWindow. All payload reads are bounded binary chunks. */
    fun selected(
        db: SupportSQLiteDatabase,
        signal: CancellationSignal,
        chainId: String
    ): Selected? {
        check(db.inTransaction())
        signal.throwIfCanceled()
        db.query(
            SimpleSQLiteQuery(
            "SELECT 1 FROM main.chain_nodes WHERE chainId=? AND " +
                "(typeof(isActive)<>'integer' OR isActive NOT IN (0,1) OR " +
                "typeof(isDefault)<>'integer' OR isDefault NOT IN (0,1)) LIMIT 1",
                    arrayOf<Any?>(chainId)
        ),
            signal
        ).use { check(!it.moveToFirst()) { "Endpoint selection flags are malformed" } }
        val rowId = db.query(
            SimpleSQLiteQuery(
            "SELECT rowid FROM main.chain_nodes WHERE chainId=? AND isActive=1 LIMIT 2", arrayOf<Any?>(chainId)
        ),
            signal
        ).use {
            signal.throwIfCanceled()
            if (!it.moveToFirst()) return null
            check(it.columnCount == 1)
            it.endpointInteger(0).also { _ -> check(!it.moveToNext()) { "Endpoint selection is ambiguous" } }
        }
        return Selected(rowId, fingerprint(db, signal, rowId))
    }

    private fun fingerprint(
        db: SupportSQLiteDatabase,
        signal: CancellationSignal,
        rowId: Long
    ): String {
        val fields = listOf("chainId", "url", "name")
        val projection = fields.joinToString(",") { "typeof($it),length(CAST($it AS BLOB))" }
        val hash = EndpointDecisionDigest()
        hash.text("endpoint-live-selection-v1")
        hash.integer(rowId)
        val lengths = db.query(
            SimpleSQLiteQuery(
            "SELECT isDefault,$projection FROM main.chain_nodes WHERE rowid=? AND isActive=1 LIMIT 1", arrayOf<Any?>(rowId)
        ),
            signal
        ).use { cursor ->
            signal.throwIfCanceled()
            check(cursor.columnCount == 7 && cursor.moveToFirst()) { "Endpoint selected row disappeared" }
            val isDefault = cursor.endpointInteger(0)
            check(isDefault in 0L..1L)
            hash.integer(isDefault)
            var remaining = EndpointDecisionLimits.FINGERPRINT_BYTES
            fields.indices.map { index ->
                check(cursor.getType(index * 2 + 1) == Cursor.FIELD_TYPE_STRING && cursor.getString(index * 2 + 1) == "text") {
                    "Endpoint selected row text is malformed"
                }
                cursor.endpointInteger(index * 2 + 2).also {
                    check(it in 0..remaining) { "Endpoint selected row exceeds its byte budget" }
                    remaining -= it
                }
            }.also { check(!cursor.moveToNext()) }
        }
        fields.forEachIndexed { index, field ->
            hash.text(field)
            hash.text("TEXT")
            val length = lengths[index]
            hash.integer(length)
            var offset = 0L
            while (offset < length) {
                signal.throwIfCanceled()
                val count = minOf(length - offset, EndpointDecisionLimits.INTERNAL_CHUNK_BYTES.toLong()).toInt()
                db.query(
                    SimpleSQLiteQuery(
                    "SELECT substr(CAST($field AS BLOB),?,?) FROM main.chain_nodes WHERE rowid=? AND isActive=1 LIMIT 1",
                    arrayOf<Any?>(offset + 1, count, rowId)
                ),
                    signal
                ).use {
                    signal.throwIfCanceled()
                    check(it.columnCount == 1 && it.moveToFirst() && it.getType(0) == Cursor.FIELD_TYPE_BLOB)
                    val bytes = it.getBlob(0)
                    check(bytes.size == count && !it.moveToNext())
                    hash.bytes(bytes)
                }
                offset += count
            }
        }
        signal.throwIfCanceled()
        return hash.finish()
    }
}
