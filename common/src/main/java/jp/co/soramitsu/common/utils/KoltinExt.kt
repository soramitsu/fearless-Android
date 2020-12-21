package jp.co.soramitsu.common.utils

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit

val BigDecimal.isNonNegative: Boolean
    get() = signum() >= 0

fun Long.daysFromMillis() = TimeUnit.MILLISECONDS.toDays(this)

inline fun <T> List<T>.sumBy(extractor: (T) -> BigInteger) = fold(BigInteger.ZERO) { acc, element ->
    acc + extractor(element)
}