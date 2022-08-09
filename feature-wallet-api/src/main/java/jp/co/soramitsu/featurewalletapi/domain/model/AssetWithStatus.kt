package jp.co.soramitsu.featurewalletapi.domain.model

class AssetWithStatus(
    val asset: Asset,
    val enabled: Boolean,
    val hasAccount: Boolean,
    val hasChainAccount: Boolean
)
