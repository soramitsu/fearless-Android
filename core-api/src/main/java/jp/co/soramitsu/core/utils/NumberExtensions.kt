package jp.co.soramitsu.core.utils

import java.math.BigDecimal
import java.math.BigInteger

fun BigInteger?.orZero(): BigInteger = this ?: BigInteger.ZERO

fun BigDecimal?.orZero(): BigDecimal = this ?: BigDecimal.ZERO
