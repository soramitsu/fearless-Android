package jp.co.soramitsu.coredb.model.cache

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Persisted quote identity. A legacy NULL is unverified; a display symbol never supplies identity. */
sealed class CachedFiatCurrencyIdentity {
    object Unverified : CachedFiatCurrencyIdentity()
    data class Known(val value: String) : CachedFiatCurrencyIdentity()
    data class Unavailable(val reason: Reason) : CachedFiatCurrencyIdentity()
    enum class Reason { DIAGNOSTIC_MISMATCH, STORAGE_CLASS, PAYLOAD_BOUND, INVALID_UTF8, EMPTY_ID }

    fun matches(requestedId: String): Boolean =
        this is Known && requestedId.isNotBlank() && value == requestedId
}

/** Reuses the bounded physical-cell projection without parsing a currency identifier as a number. */
object CachedFiatCurrencyDecoder {
    const val MAX_PAYLOAD_BYTES = 4096

    fun decode(cell: CachedNumericCell): CachedFiatCurrencyIdentity {
        if (cell.byteCount < 0) return unavailable(CachedFiatCurrencyIdentity.Reason.DIAGNOSTIC_MISMATCH)
        if (cell.storageClass == "null") {
            return if (cell.byteCount == 0L && cell.payload == null) CachedFiatCurrencyIdentity.Unverified
            else unavailable(CachedFiatCurrencyIdentity.Reason.DIAGNOSTIC_MISMATCH)
        }
        if (cell.storageClass != "text") return unavailable(CachedFiatCurrencyIdentity.Reason.STORAGE_CLASS)
        if (cell.byteCount > MAX_PAYLOAD_BYTES) return unavailable(CachedFiatCurrencyIdentity.Reason.PAYLOAD_BOUND)
        val payload = cell.payload
        if (payload == null || payload.size.toLong() != cell.byteCount) {
            return unavailable(CachedFiatCurrencyIdentity.Reason.DIAGNOSTIC_MISMATCH)
        }
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload)).toString()
        } catch (error: CharacterCodingException) {
            return unavailable(CachedFiatCurrencyIdentity.Reason.INVALID_UTF8)
        }
        return if (text.isBlank()) unavailable(CachedFiatCurrencyIdentity.Reason.EMPTY_ID)
        else CachedFiatCurrencyIdentity.Known(text)
    }

    private fun unavailable(reason: CachedFiatCurrencyIdentity.Reason) =
        CachedFiatCurrencyIdentity.Unavailable(reason)
}

