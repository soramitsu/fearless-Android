package jp.co.soramitsu.coredb.model.chain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Read-only optional cache projection. Omitted values remain unchanged in SQL. */
internal object OptionalCatalogText {
    // This bounds one optional operational value, not its persisted representation or any user choice.
    const val MAX_BYTES = 4 * 1024 * 1024
    const val CHUNK_BYTES = 64 * 1024

    suspend fun read(length: Long?, inline: ByteArray?, chunk: suspend (offset: Int, count: Int) -> ByteArray?): String? {
        currentCoroutineContext().ensureActive()
        if (length == null || length < 0 || length > MAX_BYTES) return null
        // Small values arrive in the bounded page; no per-row suspend query is needed.
        // Reject inconsistent projections rather than returning a prefix or querying a different value.
        val bytes = if (length <= CHUNK_BYTES) {
            if (inline == null || inline.size.toLong() != length) return null
            inline
        } else {
            if (inline != null) return null
            ByteArray(length.toInt())
        }
        var offset = if (length <= CHUNK_BYTES) bytes.size else 0
        while (offset < bytes.size) {
            currentCoroutineContext().ensureActive()
            val count = minOf(bytes.size - offset, CHUNK_BYTES)
            val part = chunk(offset + 1, count) ?: return null
            if (part.size != count) return null
            part.copyInto(bytes, offset)
            offset += count
        }
        currentCoroutineContext().ensureActive()
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
