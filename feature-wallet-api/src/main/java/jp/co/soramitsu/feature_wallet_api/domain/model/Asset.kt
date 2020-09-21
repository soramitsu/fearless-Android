package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.feature_account_api.domain.model.Node

class Asset(
    val name: String,
    val networkType: Node.NetworkType,
    val amount: Double,
    val dollarRate: Double,
    val recentRateChange: Double
) {
    val dollarAmount = amount * dollarRate
}