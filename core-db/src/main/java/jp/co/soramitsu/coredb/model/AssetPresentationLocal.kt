package jp.co.soramitsu.coredb.model

/** Only the durable, wallet-scoped presentation columns of an asset row. */
data class AssetPresentationLocal(
    val chainId: String,
    val assetId: String,
    val accountId: ByteArray,
    val enabled: Long?,
    val sortIndex: Long,
    val markedNotNeed: Long,
    val chainAccountName: String?,
    val chainIdRaw: ByteArray,
    val assetIdRaw: ByteArray,
    val chainAccountNameRaw: ByteArray?,
    val chainIdStorageClass: String,
    val assetIdStorageClass: String,
    val accountIdStorageClass: String,
    val enabledStorageClass: String,
    val sortIndexStorageClass: String,
    val markedNotNeedStorageClass: String,
    val chainAccountNameStorageClass: String
)
