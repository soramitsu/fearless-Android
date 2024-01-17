package jp.co.soramitsu.common.utils.formatting

import jp.co.soramitsu.common.utils.decimalFormatterFor
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

class FiatFormatter(
    private val locale: Locale? = null
) : NumberFormatter {
    companion object {
        private const val FIAT_DECIMAL_PATTERN = "#,##0.00"
    }

    override fun format(number: BigDecimal): String {
        val delegate = decimalFormatterFor(FIAT_DECIMAL_PATTERN)
        locale?.let {
            delegate.decimalFormatSymbols = DecimalFormatSymbols(locale)
        }
        val decimalSeparator = delegate.decimalFormatSymbols.decimalSeparator
        val leadingZeros = Regex(decimalSeparator + "00$")
        return delegate.format(number).replace(leadingZeros, "")
    }
}

class FiatSmallFormatter(
    private val locale: Locale? = null
) : NumberFormatter {
    companion object {
        private const val FIAT_DECIMAL_PATTERN = "#.###E0"
    }

    override fun format(number: BigDecimal): String {
        val delegate = decimalFormatterFor(FIAT_DECIMAL_PATTERN)
        locale?.let {
            delegate.decimalFormatSymbols = DecimalFormatSymbols(locale)
        }
        val formattedValue = delegate.format(number).toBigDecimal()
        return FullPrecisionFormatter(locale).format(formattedValue)
    }
}
