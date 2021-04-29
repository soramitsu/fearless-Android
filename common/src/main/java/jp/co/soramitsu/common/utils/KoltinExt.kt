package jp.co.soramitsu.common.utils

import java.io.InputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit

val BigDecimal.isNonNegative: Boolean
    get() = signum() >= 0

fun Long.daysFromMillis() = TimeUnit.MILLISECONDS.toDays(this)

inline fun <T> List<T>.sumByBigInteger(extractor: (T) -> BigInteger) = fold(BigInteger.ZERO) { acc, element ->
    acc + extractor(element)
}

inline fun <T> List<T>.sumByBigDecimal(extractor: (T) -> BigDecimal) = fold(BigDecimal.ZERO) { acc, element ->
    acc + extractor(element)
}

fun <T> Result<T>.requireException() = exceptionOrNull()!!

fun <T> Result<T>.requireValue() = getOrThrow()!!

fun InputStream.readText() = bufferedReader().use { it.readText() }

fun <T> List<T>.second() = get(1)

@Suppress("UNCHECKED_CAST")
inline fun <K, V, R> Map<K, V>.mapValuesNotNull(crossinline mapper: (Map.Entry<K, V>) -> R?): Map<K, R> {
    return mapValues(mapper)
        .filterValues { it != null } as Map<K, R>
}

/**
 * Complexity: O(n * log(n))
 */
// TODO possible to optimize
fun List<Double>.median(): Double = sorted().let {
    val middleRight = it[it.size / 2]
    val middleLeft = it[(it.size - 1) / 2] // will be same as middleRight if list size is odd

    (middleLeft + middleRight) / 2
}

fun generateLinearSequence(initial: Int, step: Int) = generateSequence(initial) { it + step }

// carrying

// typealias Function0<R> = () -> R
// typealias Function1<T, R> = (T) -> R
// typealias Function2<T1, T2, R> = (T1, T2) -> R
// typealias Function4<T1, T2, T3, R> = (T1, T2, T3) -> R

fun <T1, R> Function1<T1, R>.carry(arg1: T1): Function0<R> = {
    invoke(arg1)
}

fun <T1, T2, R> Function2<T1, T2, R>.invoke(arg1: T1): Function1<T2, R> = { arg2 ->
    invoke(arg1, arg2)
}

fun <T1, T2, T3, R> Function3<T1, T2, T3, R>.invoke(arg1: T1): Function2<T2, T3, R> = { arg2, arg3 ->
    invoke(arg1, arg2, arg3)
}
