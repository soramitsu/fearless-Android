package jp.co.soramitsu.common.utils

import java.math.BigDecimal
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

val BigDecimal.isNonNegative: Boolean
    get() = signum() >= 0

fun Long.daysFromMillis() = TimeUnit.MILLISECONDS.toDays(this)

fun <T> concurrentHashSet() : MutableSet<T> = Collections.newSetFromMap(ConcurrentHashMap<T, Boolean>())

fun String.requirePrefix(prefix: String) = if (startsWith(prefix)) this else prefix + this