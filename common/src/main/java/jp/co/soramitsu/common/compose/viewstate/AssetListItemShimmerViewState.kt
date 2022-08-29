package jp.co.soramitsu.common.compose.viewstate

data class AssetListItemShimmerViewState(
    val assetIconUrl: String,
    val assetChainUrls: List<String> = emptyList()
)
