package jp.co.soramitsu.common.utils

import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.formatting.CompoundNumberFormatter
import jp.co.soramitsu.common.utils.formatting.FiatFormatter
import jp.co.soramitsu.common.utils.formatting.FixedPrecisionFormatter
import jp.co.soramitsu.common.utils.formatting.FullPrecisionFormatter
import jp.co.soramitsu.common.utils.formatting.NumberAbbreviation
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

const val DOLLAR_SIGN = "$"
private const val DECIMAL_PATTERN_BASE = "#,##0"

private const val MAX_DECIMALS_2 = 2
private const val MAX_DECIMALS_3 = 3
const val MAX_DECIMALS_8 = 8

private val fiatAmountFormatter = FiatFormatter()
private val percentAmountFormatter = FixedPrecisionFormatter(MAX_DECIMALS_2)
private val cryptoAmountShortFormatter = FixedPrecisionFormatter(MAX_DECIMALS_3)
private val cryptoAmountDetailFormatter = FixedPrecisionFormatter(MAX_DECIMALS_8)

private val fiatAbbreviatedFormatter = fiatAbbreviatedFormatter()
private val cryptoShortAbbreviatedFormatter = cryptoShortAbbreviatedFormatter()
private val cryptoDetailAbbreviatedFormatter = cryptoDetailAbbreviatedFormatter()
private val fullPrecisionFormatter = FullPrecisionFormatter()

fun patternWith(precision: Int): String {
    return if (precision > 0) {
        "$DECIMAL_PATTERN_BASE.${"#".repeat(precision)}"
    } else {
        DECIMAL_PATTERN_BASE
    }
}

fun BigDecimal.formatFiat() = fiatAbbreviatedFormatter.format(this)
fun BigDecimal.formatFiat(fiatSymbol: String?) = (fiatSymbol ?: DOLLAR_SIGN) + formatFiat()
fun BigDecimal.formatPercent() = percentAmountFormatter.format(this)

fun BigDecimal.formatCryptoFull() = fullPrecisionFormatter.format(this)
fun BigDecimal.formatCrypto(symbol: String? = null): String {
    return when (symbol) {
        null -> cryptoShortAbbreviatedFormatter.format(this)
        else -> cryptoShortAbbreviatedFormatter.format(this).formatWithTokenSymbol(symbol)
    }
}
fun BigDecimal.formatCryptoDetail(symbol: String? = null): String {
    return when (symbol) {
        null -> cryptoDetailAbbreviatedFormatter.format(this)
        else -> cryptoDetailAbbreviatedFormatter.format(this).formatWithTokenSymbol(symbol)
    }
}

private fun String.formatWithTokenSymbol(symbol: String) = "$this ${symbol.uppercase()}"

fun BigDecimal.formatAsPercentage(): String {
    return formatPercent() + "%"
}

fun BigDecimal.formatAsChange(): String {
    val prefix = if (isNonNegative) "+" else ""

    return prefix + formatAsPercentage()
}

fun Long.formatDaysSinceEpoch(context: Context): String? {
    val currentDays = System.currentTimeMillis().daysFromMillis()
    val diff = currentDays - this

    if (diff < 0) {
        Log.e(
            "jp.co.soramitsu.common.utils.NumberFormattersKt.formatDaysSinceEpoch",
            "Error: diff < 0: ",
            IllegalArgumentException("Past date should be less than current")
        )
    }

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

fun Long.formatDateTime() = SimpleDateFormat.getDateInstance().format(Date(this))

fun decimalFormatterFor(pattern: String) = DecimalFormat(pattern).apply {
    roundingMode = RoundingMode.FLOOR
}

fun fiatAbbreviatedFormatter() = CompoundNumberFormatter(
    abbreviations = listOf(
        NumberAbbreviation(BigDecimal.ZERO, BigDecimal.ONE, "", fiatAmountFormatter),
        NumberAbbreviation(BigDecimal.ONE, BigDecimal.ONE, "", fiatAmountFormatter),
        NumberAbbreviation(BigDecimal("1E+3"), BigDecimal.ONE, "", fiatAmountFormatter),
        NumberAbbreviation(BigDecimal("1E+6"), BigDecimal("1E+6"), "M", fiatAmountFormatter),
        NumberAbbreviation(BigDecimal("1E+9"), BigDecimal("1E+9"), "B", fiatAmountFormatter),
        NumberAbbreviation(BigDecimal("1E+12"), BigDecimal("1E+12"), "T", fiatAmountFormatter)
    )
)

fun cryptoDetailAbbreviatedFormatter() = CompoundNumberFormatter(
    abbreviations = listOf(
        NumberAbbreviation(BigDecimal.ZERO, BigDecimal.ONE, "", cryptoAmountDetailFormatter),
        NumberAbbreviation(BigDecimal.ONE, BigDecimal.ONE, "", cryptoAmountDetailFormatter),
        NumberAbbreviation(BigDecimal("1E+3"), BigDecimal.ONE, "", cryptoAmountShortFormatter),
        NumberAbbreviation(BigDecimal("1E+6"), BigDecimal("1E+6"), "M", cryptoAmountDetailFormatter),
        NumberAbbreviation(BigDecimal("1E+9"), BigDecimal("1E+9"), "B", cryptoAmountDetailFormatter),
        NumberAbbreviation(BigDecimal("1E+12"), BigDecimal("1E+12"), "T", cryptoAmountDetailFormatter),
        NumberAbbreviation(BigDecimal("1E+15"), BigDecimal("1E+12"), "T", cryptoAmountShortFormatter)
    )
)

fun cryptoShortAbbreviatedFormatter() = CompoundNumberFormatter(
    abbreviations = listOf(
        NumberAbbreviation(BigDecimal.ZERO, BigDecimal.ONE, "", cryptoAmountShortFormatter),
        NumberAbbreviation(BigDecimal.ONE, BigDecimal.ONE, "", cryptoAmountShortFormatter),
        NumberAbbreviation(BigDecimal("1E+3"), BigDecimal.ONE, "", cryptoAmountShortFormatter),
        NumberAbbreviation(BigDecimal("1E+6"), BigDecimal("1E+6"), "M", cryptoAmountShortFormatter),
        NumberAbbreviation(BigDecimal("1E+9"), BigDecimal("1E+9"), "B", cryptoAmountShortFormatter),
        NumberAbbreviation(BigDecimal("1E+12"), BigDecimal("1E+12"), "T", cryptoAmountShortFormatter)
    )
)
