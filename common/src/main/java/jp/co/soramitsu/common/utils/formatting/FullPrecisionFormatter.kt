package jp.co.soramitsu.common.utils.formatting

import jp.co.soramitsu.common.utils.decimalFormatterFor
import jp.co.soramitsu.common.utils.patternWith
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

class FullPrecisionFormatter(
    private val locale: Locale? = null
) : NumberFormatter {

    override fun format(number: BigDecimal): String {
        val requiredPrecision = number.scale()

        return decimalFormatterFor(patternWith(requiredPrecision)).apply {
            locale?.let { decimalFormatSymbols = DecimalFormatSymbols(locale) }
        }.format(number)
    }
}
