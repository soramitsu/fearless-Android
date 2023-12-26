package jp.co.soramitsu.account.api.domain.model

import jp.co.soramitsu.common.utils.DOLLAR_SIGN
import java.math.BigDecimal

class AssetBalance(
    val assetBalance: BigDecimal,
    val rateChange: BigDecimal?,
    val assetSymbol: String?,
    val fiatBalance: BigDecimal,
    val fiatBalanceChange: BigDecimal,
    val fiatSymbol: String,
) {
    companion object {
        val Empty = AssetBalance(
            assetBalance = BigDecimal.ZERO,
            rateChange = BigDecimal.ZERO,
            assetSymbol = null,
            fiatBalance = BigDecimal.ZERO,
            fiatBalanceChange = BigDecimal.ZERO,
            fiatSymbol = DOLLAR_SIGN
        )
    }
}