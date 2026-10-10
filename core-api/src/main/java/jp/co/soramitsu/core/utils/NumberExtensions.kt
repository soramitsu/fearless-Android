package jp.co.soramitsu.core.utils

import java.math.BigDecimal
import java.math.BigInteger

fun BigInteger?.orZero(): BigInteger = this ?: BigInteger.ZERO

fun BigDecimal?.orZero(): BigDecimal = this ?: BigDecimal.ZERO

private val INT_MIN_BIG_INTEGER = BigInteger.valueOf(Int.MIN_VALUE.toLong())
private val INT_MAX_BIG_INTEGER = BigInteger.valueOf(Int.MAX_VALUE.toLong())
private val LONG_MIN_BIG_INTEGER = BigInteger.valueOf(Long.MIN_VALUE)
private val LONG_MAX_BIG_INTEGER = BigInteger.valueOf(Long.MAX_VALUE)

fun BigInteger.toIntExact(): Int {
    if (this < INT_MIN_BIG_INTEGER || this > INT_MAX_BIG_INTEGER) {
        throw ArithmeticException("BigInteger out of int range")
    }

    return toInt()
}

fun BigInteger.toLongExact(): Long {
    if (this < LONG_MIN_BIG_INTEGER || this > LONG_MAX_BIG_INTEGER) {
        throw ArithmeticException("BigInteger out of long range")
    }

    return toLong()
}
