package jp.co.soramitsu.common.compose.viewstate

data class AssetListItemViewState(
    val assetIconUrl: String,
    val assetName: String,
    val assetChainName: String,
    val assetSymbol: String,
    val displayName: String,
    val assetTokenFiat: String?,
    val assetTokenRate: String?,
    @Deprecated("replaced by Transferable amounts, not used currently")
    val assetBalance: String?,
    @Deprecated("replaced by Transferable amounts, not used currently")
    val assetBalanceFiat: String?,
    val assetTransferableBalance: String?,
    val assetTransferableBalanceFiat: String?,
    val assetChainUrls: Map<String, String>,
    val chainId: String,
    val chainAssetId: String,
    val isSupported: Boolean,
    val isHidden: Boolean,
    val hasAccount: Boolean,
    val priceId: String?,
    val hasNetworkIssue: Boolean
) {
    val key = listOf(chainAssetId, chainId, isHidden).joinToString()
}
