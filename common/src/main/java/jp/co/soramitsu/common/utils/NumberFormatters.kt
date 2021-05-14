package jp.co.soramitsu.common.utils

import android.content.Context
import android.text.format.DateUtils
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.formatting.CompoundNumberFormatter
import jp.co.soramitsu.common.utils.formatting.DynamicPrecisionFormatter
import jp.co.soramitsu.common.utils.formatting.FixedPrecisionFormatter
import jp.co.soramitsu.common.utils.formatting.NumberAbbreviation
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

private const val DECIMAL_PATTERN_BASE = "###,###."

private const val GROUPING_SEPARATOR = ','
private const val DECIMAL_SEPARATOR = '.'

private const val FULL_PRECISION = 5
private const val ABBREVIATED_PRECISION = 2

private val defaultAbbreviationFormatter = FixedPrecisionFormatter(ABBREVIATED_PRECISION)
private val defaultFullFormatter = FixedPrecisionFormatter(FULL_PRECISION)

private val thousandAbbreviation = NumberAbbreviation(
    threshold = BigDecimal("1E+3"),
    divisor = BigDecimal.ONE,
    suffix = "",
    formatter = defaultAbbreviationFormatter
)

private val millionAbbreviation = NumberAbbreviation(
    threshold = BigDecimal("1E+6"),
    divisor = BigDecimal("1E+6"),
    suffix = "M",
    formatter = defaultAbbreviationFormatter
)

private val billionAbbreviation = NumberAbbreviation(
    threshold = BigDecimal("1E+9"),
    divisor = BigDecimal("1E+9"),
    suffix = "B",
    formatter = defaultAbbreviationFormatter
)

private val trillionAbbreviation = NumberAbbreviation(
    threshold = BigDecimal("1E+12"),
    divisor = BigDecimal("1E+12"),
    suffix = "T",
    formatter = defaultAbbreviationFormatter
)

private val defaultNumberFormatter = defaultNumberFormatter()
private val currencyFormatter = currencyFormatter()

fun BigDecimal.formatAsCurrency(): String {
    return "$" + currencyFormatter.format(this)
}

fun BigDecimal.format(): String {
    return defaultNumberFormatter.format(this)
}

fun BigDecimal.formatAsChange(): String {
    val prefix = if (isNonNegative) "+" else ""

    return prefix + formatAsPercentage()
}

fun BigDecimal.formatAsPercentage(): String {
    return defaultAbbreviationFormatter.format(this) + "%"
}

fun Long.formatDaysSinceEpoch(context: Context): String? {
    val currentDays = System.currentTimeMillis().daysFromMillis()
    val diff = currentDays - this

    if (diff < 0) throw IllegalArgumentException("Past date should be less than current")

    return when (diff) {
        0L -> context.getString(R.string.today)
        1L -> context.getString(R.string.yesterday)
        else -> {
            val inMillis = TimeUnit.DAYS.toMillis(this)
            DateUtils.formatDateTime(context, inMillis, 0)
        }
    }
}

fun Long.formatDateFromMillis(context: Context) = DateUtils.formatDateTime(context, this, 0)

fun Long.formatDateTime(context: Context) = DateUtils.getRelativeDateTimeString(context, this, DateUtils.SECOND_IN_MILLIS, 0, 0)

fun decimalFormatterFor(pattern: String): DecimalFormat {
    return DecimalFormat(pattern).apply {
        val symbols = decimalFormatSymbols

        symbols.groupingSeparator = GROUPING_SEPARATOR
        symbols.decimalSeparator = DECIMAL_SEPARATOR

        decimalFormatSymbols = symbols

        roundingMode = RoundingMode.FLOOR
        decimalFormatSymbols = decimalFormatSymbols
    }
}

fun patternWith(precision: Int) = "$DECIMAL_PATTERN_BASE${"#".repeat(precision)}"

fun defaultNumberFormatter() = CompoundNumberFormatter(
    abbreviations = listOf(
        NumberAbbreviation(
            threshold = BigDecimal.ZERO,
            divisor = BigDecimal.ONE,
            suffix = "",
            formatter = DynamicPrecisionFormatter(minPrecision = FULL_PRECISION)
        ),
        NumberAbbreviation(
            threshold = BigDecimal.ONE,
            divisor = BigDecimal.ONE,
            suffix = "",
            formatter = defaultFullFormatter
        ),
        thousandAbbreviation,
        millionAbbreviation,
        billionAbbreviation,
        trillionAbbreviation
    )
)

fun currencyFormatter() = CompoundNumberFormatter(
    abbreviations = listOf(
        NumberAbbreviation(
            threshold = BigDecimal.ZERO,
            divisor = BigDecimal.ONE,
            suffix = "",
            formatter = DynamicPrecisionFormatter(minPrecision = ABBREVIATED_PRECISION)
        ),
        NumberAbbreviation(
            threshold = BigDecimal.ONE,
            divisor = BigDecimal.ONE,
            suffix = "",
            formatter = defaultAbbreviationFormatter
        ),
        thousandAbbreviation,
        millionAbbreviation,
        billionAbbreviation,
        trillionAbbreviation
    )
)
