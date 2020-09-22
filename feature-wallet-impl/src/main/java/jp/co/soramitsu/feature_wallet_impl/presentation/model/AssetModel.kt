package jp.co.soramitsu.feature_wallet_impl.presentation.model

import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.R

data class AssetModel(
    val token: Asset.Token,
    val balance: Double,
    val dollarRate: Double,
    val recentRateChange: Double
) {
    val dollarAmount = balance * dollarRate

    val icon = determineIcon()

    private fun determineIcon(): Int {
        return when (token) {
            Asset.Token.KSM -> R.drawable.ic_token_ksm
            else -> throw IllegalArgumentException("Only Kusama is supported")
        }
    }
}

fun Asset.toUiModel(): AssetModel {
    return AssetModel(
        token = token,
        balance = balance,
        dollarRate = dollarRate,
        recentRateChange = recentRateChange
    )
}