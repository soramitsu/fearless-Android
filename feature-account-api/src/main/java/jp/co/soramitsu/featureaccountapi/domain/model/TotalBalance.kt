package jp.co.soramitsu.featureaccountapi.domain.model

import java.math.BigDecimal
import jp.co.soramitsu.common.utils.DOLLAR_SIGN

data class TotalBalance(val balance: BigDecimal, val fiatSymbol: String) {
    companion object {
        val Empty = TotalBalance(BigDecimal.ZERO, DOLLAR_SIGN)
    }
}
