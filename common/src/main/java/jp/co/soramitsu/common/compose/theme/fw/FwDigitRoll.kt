package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.runtime.Immutable

/**
 * One character of a rolling amount. [previous] is the character that stood in the same place
 * (aligned from the right, so decimals stay put), or null for a new leading position.
 */
@Immutable
data class FwRollColumn(val char: Char, val previous: Char?, val rolls: Boolean, val changed: Boolean)

/**
 * How a displayed amount changes: per-character columns, the index where the changed part starts
 * (tinted up/down while it settles) and the direction (+1 up, -1 down, 0 unknown or equal).
 */
@Immutable
data class FwRollPlan(val columns: List<FwRollColumn>, val changedFrom: Int, val direction: Int) {
    val text: String get() = String(columns.map { it.char }.toCharArray())
    val unchanged: Boolean get() = changedFrom >= columns.size
}

/**
 * Plans the digit roll from [old] to [new] formatted amounts. Strings are aligned from the right;
 * a column rolls when both characters are digits and differ, every other difference is a plain
 * swap. The changed part runs from the leftmost differing column to the end, which is what the
 * spec tints with up/down.
 */
fun fwDigitRollPlan(old: String?, new: String): FwRollPlan {
    if (old == null) {
        return FwRollPlan(new.map { FwRollColumn(it, it, rolls = false, changed = false) }, new.length, 0)
    }
    val offset = old.length - new.length
    var changedFrom = new.length
    val columns = new.mapIndexed { index, char ->
        val previous = old.getOrNull(index + offset).takeIf { index + offset >= 0 }
        val changed = previous != char
        if (changed && changedFrom == new.length) changedFrom = index
        FwRollColumn(char, previous, rolls = changed && previous != null && previous.isDigit() && char.isDigit(), changed = changed)
    }
    val lengthChanged = old.length != new.length
    return FwRollPlan(columns, if (lengthChanged) 0 else changedFrom, fwCompareAmounts(old, new)?.let { -it } ?: 0)
}

/**
 * Compares two amounts formatted the same way ("$12,814.22", "12 814,22 ₽"): -1, 0 or 1 when [a] is
 * smaller, equal or larger than [b]; null when either has no digits or the decimal parts differ in
 * length (the strings then cannot be compared without parsing a locale).
 */
fun fwCompareAmounts(a: String, b: String): Int? {
    val left = digitsOf(a) ?: return null
    val right = digitsOf(b) ?: return null
    if (left.second != right.second) return null
    val x = left.first.trimStart('0')
    val y = right.first.trimStart('0')
    if (x.length != y.length) return if (x.length < y.length) -1 else 1
    return x.compareTo(y).coerceIn(-1, 1)
}

/**
 * Digits of [text] and the number of digits after its last '.' or ',' separator. Two amounts in the
 * same format share that count, which is all a comparison of their digit strings needs.
 */
private fun digitsOf(text: String): Pair<String, Int>? {
    val digits = text.filter { it.isDigit() }
    if (digits.isEmpty()) return null
    if (text.trimStart().let { it.startsWith("-") || it.startsWith("−") }) return null
    val last = text.indexOfLast { it.isDigit() }
    val separator = text.lastIndexOfAny(charArrayOf('.', ','), last)
    val fraction = if (separator < 0) 0 else text.substring(separator + 1, last + 1).count { it.isDigit() }
    return digits to fraction
}
