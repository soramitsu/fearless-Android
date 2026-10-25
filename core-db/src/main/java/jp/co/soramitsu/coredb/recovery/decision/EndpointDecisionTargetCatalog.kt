package jp.co.soramitsu.coredb.recovery.decision

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import java.nio.charset.CharacterCodingException
import jp.co.soramitsu.coredb.recovery.endpointInteger

/**
 * Bounded local choices, including inactive networks without operational nodes.
 * Run off the main thread with the caller's cancellation signal and deadline.
 * Invalid identities remain unavailable; invalid optional display names remain absent.
 */
class EndpointDecisionTargetCatalog(private val database: SupportSQLiteDatabase) {
    /** Capture the page, target identities and navigation flags in one cancellable SELECT snapshot. */
    fun readPage(
        signal: CancellationSignal,
        afterExclusiveRowId: Long? = null,
        beforeExclusiveRowId: Long? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): EndpointDecisionTargetPage {
        require(pageSize in 1..MAXIMUM_PAGE_SIZE)
        require(afterExclusiveRowId == null || beforeExclusiveRowId == null)
        signal.throwIfCanceled()
        check(!database.inTransaction()) { "Endpoint catalog cannot join an outer transaction" }
        requireUtf8(signal)
        val reverse = beforeExclusiveRowId != null
        val boundary = beforeExclusiveRowId ?: afterExclusiveRowId
        val where = if (boundary == null) "" else " WHERE c.rowid ${if (reverse) "<" else ">"} ?"
        val arguments: Array<out Any?> = boundary?.let { arrayOf(it) } ?: emptyArray()
        val rows = ArrayList<EndpointDecisionTargetOption>(pageSize)
        var earlier = false
        var later = false
        signal.throwIfCanceled()
        database.query(SimpleSQLiteQuery(
            "SELECT c.rowid,${decisionTextProjection("c.id", EndpointDecisionLimits.TARGET_ID_BYTES)}," +
                "${decisionTextProjection("c.name", EndpointDecisionLimits.LABEL_BYTES)}," +
                "${decisionTextProjection("c.ecosystem", 64)}," +
                "CASE WHEN typeof(c.isEthereumBased)='integer' THEN c.isEthereumBased END," +
                "CASE WHEN typeof(c.isEthereumChain)='integer' THEN c.isEthereumChain END," +
                "CASE WHEN typeof(c.isTestNet)='integer' THEN c.isTestNet END," +
                "typeof(c.history_type),${decisionTextProjection("c.history_type", 64)}," +
                "EXISTS(SELECT 1 FROM main.chains d WHERE d.id=c.id AND d.rowid<>c.rowid LIMIT 1)," +
                "EXISTS(SELECT 1 FROM main.chains p WHERE p.rowid<c.rowid LIMIT 1)," +
                "EXISTS(SELECT 1 FROM main.chains n WHERE n.rowid>c.rowid LIMIT 1)" +
                " FROM main.chains c$where ORDER BY c.rowid ${if (reverse) "DESC" else "ASC"} LIMIT $pageSize",
            arguments
        ), signal).use { cursor ->
            signal.throwIfCanceled()
            check(cursor.columnCount == 12)
            while (cursor.moveToNext()) {
                signal.throwIfCanceled()
                check(rows.size < pageSize)
                val rowId = cursor.endpointInteger(0)
                check(boundary == null || if (reverse) rowId < boundary else rowId > boundary)
                check(rows.isEmpty() || if (reverse) rowId < rows.last().rowId else rowId > rows.last().rowId)
                val before = cursor.endpointInteger(10)
                val after = cursor.endpointInteger(11)
                check(before in 0L..1L && after in 0L..1L)
                if (rows.isEmpty()) {
                    if (reverse) later = after == 1L else earlier = before == 1L
                }
                if (reverse) earlier = before == 1L else later = after == 1L
                val name = cursor.boundedOptionalText(2, EndpointDecisionLimits.LABEL_BYTES)
                rows += EndpointDecisionTargetOption(rowId, name, availableTarget(cursor, signal))
            }
        }
        signal.throwIfCanceled()
        if (rows.isEmpty()) return EndpointDecisionTargetPage(emptyList(), null, null)
        if (reverse) rows.reverse()
        return EndpointDecisionTargetPage(rows,
            if (earlier) rows.first().rowId else null,
            if (later) rows.last().rowId else null)
    }

    private fun requireUtf8(signal: CancellationSignal) {
        signal.throwIfCanceled()
        database.query(SimpleSQLiteQuery("PRAGMA main.encoding"), signal).use { cursor ->
            signal.throwIfCanceled()
            check(cursor.columnCount == 1 && cursor.moveToFirst() && cursor.getType(0) == Cursor.FIELD_TYPE_STRING)
            check(cursor.getString(0) == "UTF-8" && !cursor.moveToNext()) {
                "Endpoint catalog requires UTF-8 database encoding"
            }
        }
        signal.throwIfCanceled()
    }

    private fun availableTarget(cursor: Cursor, signal: CancellationSignal): EndpointDecisionTarget? {
        signal.throwIfCanceled()
        return try {
            val id = cursor.boundedOptionalText(1, EndpointDecisionLimits.TARGET_ID_BYTES)
            val ambiguous = cursor.endpointInteger(9)
            check(ambiguous in 0L..1L)
            if (id.isNullOrEmpty() || ambiguous == 1L) return null
            val ecosystem = cursor.decisionText(3, 64)
            val ethereumBased = cursor.endpointInteger(4)
            val ethereumChain = cursor.endpointInteger(5)
            val testnet = cursor.endpointInteger(6)
            check(listOf(ethereumBased, ethereumChain, testnet).all { it in 0L..1L })
            check(cursor.getType(7) == Cursor.FIELD_TYPE_STRING)
            val historyType = when (cursor.getString(7)) {
                "null" -> { check(cursor.isNull(8)); null }
                "text" -> cursor.decisionText(8, 64)
                else -> error("Endpoint target history metadata differs")
            }
            signal.throwIfCanceled()
            EndpointDecisionTargetReader.capture(id, ecosystem, ethereumBased == 1L,
                ethereumChain == 1L, testnet == 1L, historyType)
        } catch (_: IllegalArgumentException) {
            signal.throwIfCanceled(); null
        } catch (_: IllegalStateException) {
            signal.throwIfCanceled(); null
        } catch (_: CharacterCodingException) {
            signal.throwIfCanceled(); null
        }
    }

    private fun Cursor.boundedOptionalText(index: Int, maximum: Int): String? {
        if (isNull(index)) return null
        return try { decisionText(index, maximum) } catch (_: CharacterCodingException) { null }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 25
        const val MAXIMUM_PAGE_SIZE = 50
    }
}
