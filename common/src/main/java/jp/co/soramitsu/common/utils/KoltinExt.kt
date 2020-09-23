package jp.co.soramitsu.common.utils

import java.math.BigDecimal

val BigDecimal.isNonNegative: Boolean
    get() = signum() >= 0