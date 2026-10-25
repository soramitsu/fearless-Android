package jp.co.soramitsu.androidfoundation.format

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class StringPair(val first: String, val second: String)

val Big100: BigDecimal = BigDecimal(100)

fun BigDecimal?.isZero(): Boolean = this == null || compareTo(BigDecimal.ZERO) == 0

fun BigInteger?.isZero(): Boolean = this == null || compareTo(BigInteger.ZERO) == 0

fun BigDecimal?.orZero(): BigDecimal = this ?: BigDecimal.ZERO

fun BigInteger?.orZero(): BigInteger = this ?: BigInteger.ZERO

fun compareNullDesc(current: BigDecimal?, next: BigDecimal?): Int {
    return when {
        current == null && next == null -> 0
        current == null -> 1
        next == null -> -1
        else -> next.compareTo(current)
    }
}

fun String.addHexPrefix(): String = if (startsWith("0x")) this else "0x$this"

fun mapBalance(balance: BigInteger, precision: Int): BigDecimal {
    return BigDecimal(balance).movePointLeft(precision)
}

fun mapBalance(balance: BigDecimal, precision: Int): BigInteger {
    return balance.movePointRight(precision).setScale(0, RoundingMode.DOWN).toBigInteger()
}

inline fun <reified T> Any?.safeCast(): T? = this as? T

fun BigDecimal.divideBy(divisor: BigDecimal, precision: Int? = null): BigDecimal {
    require(divisor.compareTo(BigDecimal.ZERO) != 0) { "Cannot divide by zero" }
    return precision?.let {
        divide(divisor, it, RoundingMode.HALF_UP).stripTrailingZeros()
    } ?: divide(divisor, 18, RoundingMode.HALF_UP).stripTrailingZeros()
}

fun BigDecimal.safeDivide(divisor: BigDecimal, precision: Int? = null): BigDecimal {
    return if (divisor.compareTo(BigDecimal.ZERO) == 0) {
        BigDecimal.ZERO
    } else {
        divideBy(divisor, precision)
    }
}

fun BigDecimal.equalTo(other: BigDecimal): Boolean = compareTo(other) == 0
