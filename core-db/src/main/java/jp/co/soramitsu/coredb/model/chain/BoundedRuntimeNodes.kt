package jp.co.soramitsu.coredb.model.chain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Read-only operational projection. It grants neither a selection nor permission to connect. */
internal object BoundedRuntimeNodes {
    const val PAGE_ROWS = 64
    const val CHUNK_BYTES = 64 * 1024
    const val INLINE_TEXT_BYTES = 4096
    enum class Field { URL, NAME }
    enum class Selection { AVAILABLE, MISSING, AMBIGUOUS, UNREADABLE }
    data class Result(val nodes: List<ChainNodeLocal>, val selection: Selection)

    suspend fun read(
        chainId: String,
        page: suspend (Long?) -> List<RuntimeNodeMetadata>,
        chunk: suspend (Long, Field, Int, Int) -> ByteArray?
    ): Result {
        val nodes = mutableListOf<ChainNodeLocal>()
        var after: Long? = null
        var activeRows = 0
        var unreadableActive = false
        var malformedFlags = false
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = page(after)
            check(rows.size <= PAGE_ROWS) { "Runtime endpoint page exceeded its bound" }
            if (rows.isEmpty()) break
            for (row in rows) {
                check(after == null || row.rowId > checkNotNull(after)) { "Runtime endpoint page order changed" }
                after = row.rowId
                val flagsValid = row.active != null && row.active in 0L..1L && row.default != null && row.default in 0L..1L
                // An unknown active flag can hide a competing selection. A malformed default
                // flag on an explicitly inactive row cannot do so; omit that unreadable row.
                if (row.active == null || row.active !in 0L..1L) malformedFlags = true
                if (row.active == 1L) activeRows = minOf(2, activeRows + 1)
                val value = if (flagsValid) decode(chainId, row, chunk) else null
                if (value == null && row.active == 1L) unreadableActive = true
                if (value != null) nodes += value
            }
        }
        val selection = when {
            malformedFlags || unreadableActive -> Selection.UNREADABLE
            activeRows > 1 -> Selection.AMBIGUOUS
            activeRows == 0 -> Selection.MISSING
            else -> Selection.AVAILABLE
        }
        return Result(if (selection == Selection.AVAILABLE) nodes else nodes.map { it.copy(isActive = false) }, selection)
    }

    private suspend fun decode(
        chainId: String,
        row: RuntimeNodeMetadata,
        chunk: suspend (Long, Field, Int, Int) -> ByteArray?
    ): ChainNodeLocal? {
        val lengths = listOf(row.chainBytes, row.urlBytes, row.nameBytes)
        var remaining = EndpointProjectionLimits.MAX_TEXT_BYTES
        for (length in lengths) {
            if (length == null || length < 0 || length > remaining) return null
            remaining -= length
        }
        if (row.chainBytes != chainId.toByteArray(Charsets.UTF_8).size.toLong()) return null
        val url = text(row.rowId, Field.URL, checkNotNull(row.urlBytes).toInt(), row.urlInline, chunk) ?: return null
        val name = text(row.rowId, Field.NAME, checkNotNull(row.nameBytes).toInt(), row.nameInline, chunk) ?: return null
        return ChainNodeLocal(chainId, url, name, row.active == 1L, row.default == 1L)
    }

    private suspend fun text(
        rowId: Long,
        field: Field,
        length: Int,
        inline: ByteArray?,
        chunk: suspend (Long, Field, Int, Int) -> ByteArray?
    ): String? {
        if (inline != null && (inline.size != length || inline.size > INLINE_TEXT_BYTES)) return null
        val bytes = inline ?: ByteArray(length)
        var offset = if (inline == null) 0 else length
        while (offset < length) {
            currentCoroutineContext().ensureActive()
            val count = minOf(length - offset, CHUNK_BYTES)
            val part = chunk(rowId, field, offset + 1, count) ?: return null
            if (part.size != count) return null
            part.copyInto(bytes, offset)
            offset += count
        }
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
