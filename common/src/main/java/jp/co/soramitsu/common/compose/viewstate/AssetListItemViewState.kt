package jp.co.soramitsu.common.compose.viewstate

data class AssetListItemViewState(
    val index: Int?,
    val assetIconUrl: String,
    val assetName: String,
    val assetChainName: String,
    val assetSymbol: String,
    val assetTokenFiat: String?,
    val assetTokenRate: String?,
    val assetTransferableBalance: String?,
    val assetTransferableBalanceFiat: String?,
    val assetChainUrls: Map<String, String>,
    val chainId: String,
    val chainAssetId: String,
    val isSupported: Boolean,
    val isHidden: Boolean,
    val assetPreference: String = "auto",
    val isTestnet: Boolean,
    val ecosystemId: String = "",
    val canonicalAssetKey: String = listOf(ecosystemId, chainId, chainAssetId).joinToString(":"),
    val metadataTrust: String = "verified",
    val metadataSource: String = "registry",
    val priceTrust: String = "untrusted",
    val networkFiatSubtotal: String? = null,
    val networkAssetCount: Int = 0,
    val networkAccountLabel: String? = null,
    val networkLastSuccessMillis: Long? = null,
    val networkIsStale: Boolean = false,
    val networkSyncError: String? = null,
    val networkScanCoverage: String? = null
) {
    val key = listOf(index ?: 0, canonicalAssetKey, isHidden).joinToString()

    val isDetected: Boolean = metadataTrust != "verified" && assetPreference == "auto"
}
