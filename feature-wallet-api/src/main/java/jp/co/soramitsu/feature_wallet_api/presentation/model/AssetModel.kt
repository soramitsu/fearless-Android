package jp.co.soramitsu.feature_wallet_api.presentation.model

data class AssetModel(
    val chainId: String,
    val chainAssetId: Int,
    val tokenIconRes: Int,
    val imageUrl: String,
    val tokenName: String,
    val assetBalance: String
)
