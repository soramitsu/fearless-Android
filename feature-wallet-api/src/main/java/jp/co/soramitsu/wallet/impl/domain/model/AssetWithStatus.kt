package jp.co.soramitsu.wallet.impl.domain.model

class AssetWithStatus(
    val asset: Asset,
    val enabled: Boolean,
    val hasAccount: Boolean,
    val hasChainAccount: Boolean
)
