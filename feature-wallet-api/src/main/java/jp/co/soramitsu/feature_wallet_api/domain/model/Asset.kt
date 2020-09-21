package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.feature_account_api.domain.model.Node

class Asset(
    val token: Token,
    val balance: Double,
    val dollarRate: Double,
    val recentRateChange: Double
) {
    val dollarAmount = balance * dollarRate

    enum class Token(val displayName: String, val networkType: Node.NetworkType) {
        KSM("KSM", Node.NetworkType.KUSAMA)
    }
}