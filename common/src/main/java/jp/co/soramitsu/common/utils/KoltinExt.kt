package jp.co.soramitsu.common.utils

import java.math.BigDecimal
import java.util.concurrent.TimeUnit

val BigDecimal.isNonNegative: Boolean
    get() = signum() >= 0

fun Long.daysFromMillis() = TimeUnit.MILLISECONDS.toDays(this)

fun String.requirePrefix(prefix: String) = if (startsWith(prefix)) this else prefix + this