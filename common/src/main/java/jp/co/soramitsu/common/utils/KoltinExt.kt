package jp.co.soramitsu.common.utils

import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlin.math.pow

val BigDecimal.isNonNegative: Boolean
    get() = signum() >= 0

fun Long.daysFromMillis() = TimeUnit.MILLISECONDS.toDays(this)