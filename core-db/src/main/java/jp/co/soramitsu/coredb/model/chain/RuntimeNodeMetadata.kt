package jp.co.soramitsu.coredb.model.chain

/** Validated scalar metadata plus at most 4 KiB per inline field; larger text uses bounded chunks. */
data class RuntimeNodeMetadata(
    val rowId: Long,
    val chainBytes: Long?,
    val urlBytes: Long?,
    val nameBytes: Long?,
    val active: Long?,
    val default: Long?,
    val urlInline: ByteArray? = null,
    val nameInline: ByteArray? = null
)
