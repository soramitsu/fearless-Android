package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.coredb.model.chain.EndpointProjectionLimits
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Validate bounded raw TEXT bytes without replacement decoding or materializing full strings. */
internal object CurrentEndpointTextAdmission {
    fun markOrdinaryUtf8(database: SupportSQLiteDatabase) {
        var after: Long? = null
        while (true) {
            val where = if (after == null) "" else " AND s.source_rowid > ?"
            val args: Array<out Any?> = after?.let { arrayOf(it) } ?: emptyArray()
            val ids = database.query(
                "SELECT s.source_rowid FROM current_endpoint_archive_v1 s " +
                    "JOIN temp.current_endpoint_adoption_keys_v1 k ON k.source_rowid = s.source_rowid " +
                    "WHERE typeof(s.chainId) = 'text' AND typeof(s.url) = 'text' AND typeof(s.name) = 'text' " +
                    "AND typeof(s.isActive) = 'integer' AND s.isActive IN (0,1) " +
                    "AND typeof(s.isDefault) = 'integer' AND s.isDefault IN (0,1) " +
                    "AND length(CAST(s.chainId AS BLOB)) + length(CAST(s.url AS BLOB)) + " +
                    "length(CAST(s.name AS BLOB)) <= ${EndpointProjectionLimits.MAX_TEXT_BYTES}$where " +
                    "ORDER BY s.source_rowid LIMIT 32", args
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        check(cursor.getType(0) == Cursor.FIELD_TYPE_INTEGER)
                        add(cursor.getLong(0))
                    }
                }
            }
            if (ids.isEmpty()) return
            check(after == null || ids.first() > checkNotNull(after)) { "Current endpoint admission did not advance" }
            for (id in ids) {
                val valid = listOf("chainId", "url", "name").all { column ->
                    database.query(
                        "SELECT CASE WHEN typeof($column) = 'text' AND length(CAST($column AS BLOB)) <= " +
                            "$TON_UPGRADE_MAX_CELL_PAYLOAD_BYTES THEN CAST($column AS BLOB) ELSE NULL END " +
                            "FROM current_endpoint_archive_v1 WHERE source_rowid = ?", arrayOf(id)
                    ).use { cursor ->
                        check(cursor.moveToFirst() && cursor.getType(0) == Cursor.FIELD_TYPE_BLOB) {
                            "Current endpoint text changed after bounded capture"
                        }
                        isExactUtf8(cursor.getBlob(0)).also { check(!cursor.moveToNext()) }
                    }
                }
                if (valid) database.execSQL(
                    "UPDATE temp.current_endpoint_adoption_keys_v1 SET operational = 1 WHERE source_rowid = ?", arrayOf(id)
                )
            }
            after = ids.last()
        }
    }

    /** Decoder output is discarded in fixed-size chunks; invalid bytes never become replacement text. */
    fun isExactUtf8(bytes: ByteArray): Boolean {
        require(bytes.size.toLong() <= TON_UPGRADE_MAX_CELL_PAYLOAD_BYTES)
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(bytes)
        val output = CharBuffer.allocate(2048)
        while (true) {
            val result = decoder.decode(input, output, true)
            if (result.isError) return false
            if (result.isUnderflow) break
            check(result.isOverflow)
            (output as java.nio.Buffer).clear()
        }
        (output as java.nio.Buffer).clear()
        return !decoder.flush(output).isError
    }
}
