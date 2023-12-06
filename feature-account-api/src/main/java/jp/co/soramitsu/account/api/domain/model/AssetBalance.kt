package jp.co.soramitsu.account.api.domain.model

import jp.co.soramitsu.common.utils.DOLLAR_SIGN
import java.math.BigDecimal

class AssetBalance(
    val balance: BigDecimal,
    val balanceChange: BigDecimal,
    val rateChange: BigDecimal?,
    val fiatSymbol: String
) {
    companion object {
        val Empty = AssetBalance(
            balance = BigDecimal.ZERO,
            balanceChange = BigDecimal.ZERO,
            rateChange = BigDecimal.ZERO,
            fiatSymbol = DOLLAR_SIGN
        )
    }
}