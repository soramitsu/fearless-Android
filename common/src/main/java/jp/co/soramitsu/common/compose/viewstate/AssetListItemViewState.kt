package jp.co.soramitsu.common.compose.viewstate

data class AssetListItemViewState(
    val assetIconUrl: String,
    val assetChainName: String,
    val assetSymbol: String,
    val assetTokenFiat: String?,
    val assetTokenRate: String?,
    val assetBalance: String,
    val assetBalanceFiat: String?,
    val assetChainUrls: List<String>,
    val chainId: String,
    val chainAssetId: String,
    val isSupported: Boolean,
    val isHidden: Boolean
)
