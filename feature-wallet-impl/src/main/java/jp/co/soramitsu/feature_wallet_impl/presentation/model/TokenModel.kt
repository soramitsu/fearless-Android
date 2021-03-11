package jp.co.soramitsu.feature_wallet_impl.presentation.model

import jp.co.soramitsu.common.utils.isNonNegative
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.R
import java.math.BigDecimal

class TokenModel(
    val type: Token.Type,
    val dollarRate: BigDecimal?,
    val recentRateChange: BigDecimal?
) {
    val rateChangeColorRes = determineChangeColor()

    private fun determineChangeColor(): Int? {
        if (recentRateChange == null) return null

        return if (recentRateChange.isNonNegative) R.color.green else R.color.red
    }
}

val Token.Type.icon: Int
    get() = when (this) {
        Token.Type.KSM -> R.drawable.ic_token_ksm
        Token.Type.WND -> R.drawable.ic_token_wnd
        Token.Type.DOT -> R.drawable.ic_token_dot
    }