package jp.co.soramitsu.common.utils

import java.math.BigDecimal

fun BigDecimal?.moreThanZero(): Boolean {
    return (this ?: BigDecimal.ZERO) > BigDecimal.ZERO
}
