package jp.co.soramitsu.account.api.domain.model

import jp.co.soramitsu.common.utils.DOLLAR_SIGN
import java.math.BigDecimal

data class TotalBalance(
    val balance: BigDecimal,
    val balanceChange: BigDecimal,
    val rateChange: BigDecimal?,
    val fiatSymbol: String
) {
    companion object {
        val Empty = TotalBalance(
            balance = BigDecimal.ZERO,
            balanceChange = BigDecimal.ZERO,
            rateChange = BigDecimal.ZERO,
            fiatSymbol = DOLLAR_SIGN
        )
    }
}
