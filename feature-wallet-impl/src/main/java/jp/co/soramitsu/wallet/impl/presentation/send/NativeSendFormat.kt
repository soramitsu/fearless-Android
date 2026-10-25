package jp.co.soramitsu.wallet.impl.presentation.send

import java.math.BigDecimal
import java.util.Locale

/** Exact authorized native-chain values: no abbreviation, rounding or scientific notation. */
object NativeSendFormat {
    fun amount(value: BigDecimal, symbol: String): String = value.stripTrailingZeros().toPlainString() + " " + symbol.uppercase(Locale.ROOT)
}
