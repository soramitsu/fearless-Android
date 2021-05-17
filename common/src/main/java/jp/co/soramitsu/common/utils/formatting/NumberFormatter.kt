package jp.co.soramitsu.common.utils.formatting

import java.math.BigDecimal

interface NumberFormatter {

    fun format(number: BigDecimal): String
}
