package jp.co.soramitsu.coredb.recovery

/** One bounded exact-byte part, never accumulated or interpreted as a network endpoint. */
class EndpointValueChunk(
    val ref: EndpointRecordRef,
    val field: EndpointField,
    val byteOffset: Long,
    val state: EndpointChunkState,
    val storage: EndpointStorage?,
    val totalByteLength: Long?,
    bytes: ByteArray?,
    val observedRevision: Long? = null,
    val ownership: EndpointOwnership? = null,
    val integerValue: Long? = null,
    val realValue: Double? = null
) {
    private val payload = bytes?.copyOf()
    val bytes: ByteArray? get() = payload?.copyOf()
    init {
        require(field in endpointSourceSpec(ref.source).fields)
        require(byteOffset in 0 until Long.MAX_VALUE)
        when (state) {
            EndpointChunkState.AVAILABLE -> {
                require(storage == EndpointStorage.TEXT || storage == EndpointStorage.BLOB)
                require(totalByteLength != null && totalByteLength >= byteOffset)
                require(payload != null && payload.size.toLong() == minOf(
                    EndpointValueChunkReader.MAXIMUM_CHUNK_BYTES.toLong(), totalByteLength - byteOffset))
                require(integerValue == null && realValue == null)
            }
            EndpointChunkState.NULL_VALUE -> {
                require(byteOffset == 0L && storage == EndpointStorage.NULL)
                require(totalByteLength == null && payload == null && integerValue == null && realValue == null)
            }
            EndpointChunkState.NON_BYTE_STORAGE -> {
                require(byteOffset == 0L && totalByteLength == null && payload == null)
                require((storage == EndpointStorage.INTEGER && integerValue != null && realValue == null) ||
                    (storage == EndpointStorage.REAL && realValue != null && integerValue == null))
            }
            else -> require(storage == null && totalByteLength == null && payload == null &&
                ownership == null && integerValue == null && realValue == null)
        }
        when {
            state == EndpointChunkState.STALE_REVISION -> require(ref.source == EndpointInventorySource.OWNED &&
                observedRevision != null && observedRevision > 0 && observedRevision != ref.revision)
            state == EndpointChunkState.SOURCE_ABSENT || state == EndpointChunkState.ROW_ABSENT -> require(observedRevision == null)
            ref.source == EndpointInventorySource.OWNED -> require(observedRevision == ref.revision && ownership != null)
            else -> require(observedRevision == null && ownership == null)
        }
    }
    val nextByteOffset: Long? get() {
        if (state != EndpointChunkState.AVAILABLE) return null
        val count = checkNotNull(payload).size.toLong()
        return if (count < checkNotNull(totalByteLength) - byteOffset) byteOffset + count else null
    }
    val previousByteOffset: Long? get() = if (state == EndpointChunkState.AVAILABLE && byteOffset > 0)
        maxOf(0L, byteOffset - EndpointValueChunkReader.MAXIMUM_CHUNK_BYTES) else null
    override fun toString(): String = "EndpointValueChunk(ref=$ref, field=$field, offset=$byteOffset, state=$state)"
}
