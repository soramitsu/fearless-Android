package jp.co.soramitsu.feature_wallet_impl.util

import android.content.Context
import android.text.format.DateUtils
import jp.co.soramitsu.common.utils.isNonNegative
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val DOLLAR_CODE = "USD"

private const val DECIMAL_PATTERN_BASE = "###,###."

private const val DEFAULT_PRECISION = 4

private const val GROUPING_SEPARATOR = ' '
private const val DECIMAL_SEPARATOR = '.'

fun BigDecimal.formatAsToken(token: Asset.Token) : String {
    return "${format()} ${token.displayName}"
}

fun BigDecimal.formatAsCurrency(): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    formatter.currency = Currency.getInstance(DOLLAR_CODE)

    return formatter.format(this)
}

fun BigDecimal.format(precision: Int = DEFAULT_PRECISION): String {
    return decimalFormatterFor(patternWith(precision)).format(this)
}

fun BigDecimal.formatAsChange(): String {
    val prefix = if (isNonNegative) "+" else ""

    val formatted = format(precision = 2)

    return "$prefix$formatted%"
}

fun Long.formatPastDay(context: Context): String? {
    val diffInMillis = System.currentTimeMillis() - this

    if (diffInMillis < 0) throw IllegalArgumentException("Past date should be less than current")

    return when (TimeUnit.MILLISECONDS.toDays(diffInMillis)) {
        0L -> context.getString(R.string.today)
        1L -> context.getString(R.string.yesterday)
        else -> DateUtils.formatDateTime(context, this, 0)
    }
}

fun Long.formatDateTime(context: Context) = DateUtils.getRelativeDateTimeString(context, this, DateUtils.SECOND_IN_MILLIS, 0, 0)

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