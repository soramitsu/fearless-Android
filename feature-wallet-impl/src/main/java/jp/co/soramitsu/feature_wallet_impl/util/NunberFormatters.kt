package jp.co.soramitsu.feature_wallet_impl.util

import jp.co.soramitsu.common.utils.isNonNegative
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private const val DOLLAR_CODE = "USD"

private const val DECIMAL_PATTERN_BASE = "###,###."

private const val DEFAULT_PRECISION = 4

private const val GROUPING_SEPARATOR = ' '
private const val DECIMAL_SEPARATOR = '.'

fun BigDecimal.formatAsCurrency(): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    formatter.currency = Currency.getInstance(DOLLAR_CODE)

    return formatter.format(this)
}

fun BigDecimal.format(precision: Int = DEFAULT_PRECISION): String {
    return decimalFormatterFor(patternWith(precision)).format(this)
}

fun BigDecimal.formatAsChange(): String {
    val prefix = if (isNonNegative) '+' else '-'

    val formatted = format(precision = 2)

    return "$prefix$formatted%"
}

private fun decimalFormatterFor(pattern: String): DecimalFormat {
    return DecimalFormat(pattern).apply {
        val symbols = decimalFormatSymbols

        symbols.groupingSeparator = GROUPING_SEPARATOR
        symbols.decimalSeparator = DECIMAL_SEPARATOR

        decimalFormatSymbols = symbols

        roundingMode = RoundingMode.FLOOR
        decimalFormatSymbols = decimalFormatSymbols
    }
}

private fun patternWith(precision: Int) = "$DECIMAL_PATTERN_BASE${"#".repeat(precision)}"