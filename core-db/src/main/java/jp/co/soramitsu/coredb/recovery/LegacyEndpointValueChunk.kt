package jp.co.soramitsu.coredb.recovery

/**
 * One current read, not a whole-value snapshot or mutation grant. Bytes are copied
 * on input and access, and diagnostics omit payload. Offsets count bytes from zero.
 */
class LegacyEndpointValueChunk internal constructor(
    val originalRowId: Long,
    val field: LegacyEndpointField,
    val byteOffset: Long,
    val state: LegacyEndpointChunkState,
    val storage: LegacyEndpointStorage?,
    val totalByteLength: Long?,
    bytes: ByteArray?
) {
    private val payload = bytes?.copyOf()

    init {
        check(byteOffset in 0 until Long.MAX_VALUE) { "Archive chunk offset is invalid" }
        when (state) {
            LegacyEndpointChunkState.AVAILABLE -> {
                check(storage == LegacyEndpointStorage.TEXT || storage == LegacyEndpointStorage.BLOB)
                check(totalByteLength != null && totalByteLength >= 0 && byteOffset <= totalByteLength)
                check(payload != null && payload.size.toLong() == minOf(
                    LegacyEndpointValueChunkReader.MAXIMUM_CHUNK_BYTES.toLong(), totalByteLength - byteOffset
                )) { "Archive chunk length and payload disagree" }
            }
            LegacyEndpointChunkState.ARCHIVE_ABSENT, LegacyEndpointChunkState.ROW_ABSENT -> {
                check(storage == null && totalByteLength == null && payload == null)
            }
            LegacyEndpointChunkState.NULL_VALUE -> {
                check(byteOffset == 0L && storage == LegacyEndpointStorage.NULL && totalByteLength == null && payload == null)
            }
            LegacyEndpointChunkState.UNSUPPORTED_STORAGE -> {
                check(byteOffset == 0L && storage in listOf(LegacyEndpointStorage.INTEGER, LegacyEndpointStorage.REAL))
                check(totalByteLength == null && payload == null)
            }
        }
    }

    val bytes: ByteArray? get() = payload?.copyOf()
    val byteCount: Int? get() = payload?.size
    val endByteOffsetExclusive: Long? get() = payload?.let { byteOffset + it.size }
    val previousByteOffset: Long? get() = if (payload != null && byteOffset > 0) {
        maxOf(0L, byteOffset - LegacyEndpointValueChunkReader.MAXIMUM_CHUNK_BYTES)
    } else null
    val nextByteOffset: Long? get() = endByteOffsetExclusive?.takeIf { it < checkNotNull(totalByteLength) }

    override fun toString(): String = "LegacyEndpointValueChunk(row=$originalRowId,field=$field,state=$state," +
        "storage=$storage,offset=$byteOffset,totalBytes=$totalByteLength,chunkBytes=$byteCount)"
}
