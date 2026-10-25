package jp.co.soramitsu.coredb.model.cache

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Read projection only. This type has no entity/writer conversion. */
data class CachedNumericCell(
    val storageClass: String,
    val byteCount: Long,
    val payload: ByteArray?
)

sealed class CachedNumericState<out T> {
    object Missing : CachedNumericState<Nothing>()
    data class Known<T>(val value: T) : CachedNumericState<T>()
    data class Unavailable(val issue: CachedNumericIssue) : CachedNumericState<Nothing>()
}

data class CachedNumericIssue(
    val reason: Reason,
    val storageClass: String,
    val byteCount: Long
) {
    enum class Reason { DIAGNOSTIC_MISMATCH, STORAGE_CLASS, PAYLOAD_BOUND, INVALID_UTF8, INVALID_NUMBER, DECIMAL_SCALE_BOUND }
}

/** Proposed read bounds; not yet admitted to the production DAO or UI. */
object CachedNumericDecoder {
    const val MAX_PAYLOAD_BYTES = 4096
    const val MAX_ABSOLUTE_DECIMAL_SCALE = 4096

    fun integer(cell: CachedNumericCell): CachedNumericState<BigInteger> =
        decode(cell) { BigInteger(it) }

    fun decimal(cell: CachedNumericCell): CachedNumericState<BigDecimal> {
        val parsed = decode(cell) { BigDecimal(it) }
        return if (parsed is CachedNumericState.Known &&
            parsed.value.scale().toLong() !in -MAX_ABSOLUTE_DECIMAL_SCALE.toLong()..MAX_ABSOLUTE_DECIMAL_SCALE.toLong()
        ) {
            unavailable(cell, CachedNumericIssue.Reason.DECIMAL_SCALE_BOUND)
        } else parsed
    }

    private fun <T> decode(cell: CachedNumericCell, parse: (String) -> T): CachedNumericState<T> {
        if (cell.byteCount < 0L) return unavailable(cell, CachedNumericIssue.Reason.DIAGNOSTIC_MISMATCH)
        if (cell.storageClass == "null") {
            return if (cell.byteCount == 0L && cell.payload == null) CachedNumericState.Missing
            else unavailable(cell, CachedNumericIssue.Reason.DIAGNOSTIC_MISMATCH)
        }
        if (cell.storageClass != "text") return unavailable(cell, CachedNumericIssue.Reason.STORAGE_CLASS)
        if (cell.byteCount > MAX_PAYLOAD_BYTES) return unavailable(cell, CachedNumericIssue.Reason.PAYLOAD_BOUND)
        val payload = cell.payload
        if (payload == null || payload.size.toLong() != cell.byteCount) {
            return unavailable(cell, CachedNumericIssue.Reason.DIAGNOSTIC_MISMATCH)
        }
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload)).toString()
        } catch (error: CharacterCodingException) {
            return unavailable(cell, CachedNumericIssue.Reason.INVALID_UTF8)
        }
        return try {
            CachedNumericState.Known(parse(text))
        } catch (error: NumberFormatException) {
            unavailable(cell, CachedNumericIssue.Reason.INVALID_NUMBER)
        }
    }

    private fun unavailable(cell: CachedNumericCell, reason: CachedNumericIssue.Reason) =
        CachedNumericState.Unavailable(CachedNumericIssue(reason, cell.storageClass, cell.byteCount))
}
