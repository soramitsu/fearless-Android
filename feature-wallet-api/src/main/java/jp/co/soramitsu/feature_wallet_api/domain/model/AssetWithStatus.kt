package jp.co.soramitsu.feature_wallet_api.domain.model

class AssetWithStatus(
    val asset: Asset,
    val enabled: Boolean,
    val hasAccount: Boolean,
    val hasChainAccount: Boolean,
)
