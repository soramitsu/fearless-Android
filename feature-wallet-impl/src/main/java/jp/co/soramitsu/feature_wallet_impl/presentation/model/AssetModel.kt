package jp.co.soramitsu.feature_wallet_impl.presentation.model

import jp.co.soramitsu.common.utils.isNonNegative
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import java.math.BigDecimal

data class AssetModel(
    val token: Asset.Token,
    val balance: BigDecimal,
    val dollarRate: BigDecimal?,
    val recentRateChange: BigDecimal?,
    val dollarAmount: BigDecimal?
) {
    val icon = determineIcon()

    val rateChangeColorRes = determineChangeColor()

    private fun determineChangeColor(): Int? {
        if (recentRateChange == null) return null

        return if (recentRateChange.isNonNegative) R.color.green else R.color.red
    }

    private fun determineIcon(): Int {
        return when (token) {
            Asset.Token.KSM -> R.drawable.ic_token_ksm
            Asset.Token.WND -> R.drawable.ic_westend_24
            Asset.Token.DOT -> R.drawable.ic_polkadot_24
        }
    }
}

fun Asset.toUiModel(): AssetModel {
    return AssetModel(
        token = token,
        balance = balance,
        dollarRate = dollarRate,
        recentRateChange = recentRateChange,
        dollarAmount = dollarAmount
    )
}