package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A formatted amount split into the text around the number and the number itself, so the number
 * can be shown counting while the currency sign, separators and digits after the point stay as the
 * app formatted them. Grouping is assumed to be in threes.
 */
data class FwAmountParts(
    val prefix: String,
    val value: BigDecimal,
    val suffix: String,
    val decimals: Int,
    val decimalSeparator: Char?,
    val groupSeparator: Char?
) {
    /** [amount] written the way the original was written. */
    fun format(amount: BigDecimal): String {
        val scaled = amount.setScale(decimals, RoundingMode.DOWN).abs()
        val plain = scaled.toPlainString()
        val integer = plain.substringBefore('.')
        val fraction = if (decimals > 0) plain.substringAfter('.', "").padEnd(decimals, '0') else ""
        val grouped = if (groupSeparator == null) integer else integer.reversed().chunked(GROUP).joinToString(groupSeparator.toString()).reversed()
        val number = if (decimals > 0 && decimalSeparator != null) grouped + decimalSeparator + fraction else grouped
        return prefix + number + suffix
    }

    companion object {
        private const val GROUP = 3
        private val NUMBER_CHARS = setOf('.', ',', ' ', ' ', '\'', ' ')

        /** Null when [text] holds no single plain number (a dash, hidden digits, two numbers). */
        fun parse(text: String): FwAmountParts? {
            val first = text.indexOfFirst { it.isDigit() }
            if (first < 0) return null
            var last = first
            var index = first
            while (index < text.length && (text[index].isDigit() || text[index] in NUMBER_CHARS)) {
                if (text[index].isDigit()) last = index
                index++
            }
            if (text.substring(last + 1).any { it.isDigit() }) return null
            val number = text.substring(first, last + 1)
            val dots = number.count { it == '.' }
            val commas = number.count { it == ',' }
            val decimalSeparator: Char? = when {
                dots > 0 && commas > 0 -> if (number.lastIndexOf('.') > number.lastIndexOf(',')) '.' else ','
                dots == 1 && !looksGrouped(number, '.') -> '.'
                commas == 1 && !looksGrouped(number, ',') -> ','
                else -> null
            }
            val integerPart = if (decimalSeparator != null) number.substringBeforeLast(decimalSeparator) else number
            val fractionPart = if (decimalSeparator != null) number.substringAfterLast(decimalSeparator) else ""
            if (fractionPart.any { !it.isDigit() }) return null
            val groupSeparator = integerPart.firstOrNull { !it.isDigit() }
            if (groupSeparator != null && integerPart.any { !it.isDigit() && it != groupSeparator }) return null
            val digits = integerPart.filter { it.isDigit() } + if (fractionPart.isNotEmpty()) ".$fractionPart" else ""
            val value = digits.toBigDecimalOrNull() ?: return null
            return FwAmountParts(text.substring(0, first), value, text.substring(last + 1), fractionPart.length, decimalSeparator, groupSeparator)
        }

        /** "1,234" or "1.234": one separator followed by exactly three digits reads as grouping. */
        private fun looksGrouped(number: String, separator: Char): Boolean {
            val after = number.substringAfterLast(separator)
            val before = number.substringBeforeLast(separator)
            return after.length == GROUP && before.isNotEmpty() && before.length <= GROUP && before.all { it.isDigit() }
        }
    }
}

/** Living balance timing (brand kit SPEC part 3). */
object FwCountUpTiming {
    const val COUNT_MS = 600

    /** One count from zero per process: the first time a total is revealed after the launch. */
    @Volatile
    var firstRevealDone: Boolean = false
}

private val CountEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * The total: on its first reveal after the launch it counts up from zero in 0.6 s; after that it
 * rolls to new values ([FwRollingText]). Accessibility and copying always get the final text.
 */
@Composable
fun FwCountUpText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = LocalFwColors.current.textPrimary) {
    val motion = fwMotion
    val parts = remember(text) { FwAmountParts.parse(text) }
    val progress = remember { Animatable(0f) }
    var counting by remember { mutableStateOf(false) }
    val eligible = parts != null && parts.value.signum() > 0 && motion.moves(FwMotionKind.BalanceRoll)
    LaunchedEffect(eligible) {
        if (!eligible || FwCountUpTiming.firstRevealDone) return@LaunchedEffect
        FwCountUpTiming.firstRevealDone = true
        counting = true
        progress.snapTo(0f)
        progress.animateTo(1f, tween(FwCountUpTiming.COUNT_MS, easing = CountEasing))
        counting = false
    }
    // The frame a real total first appears already shows zero, so the final value never flashes first.
    val showCount = parts != null && (counting || (eligible && !FwCountUpTiming.firstRevealDone))
    if (!showCount || parts == null) {
        FwRollingText(text = text, style = style, modifier = modifier, color = color)
        return
    }
    val shown = parts.format(parts.value.multiply(BigDecimal(progress.value.toDouble())))
    Text(text = shown, style = style, color = color, modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) })
}
