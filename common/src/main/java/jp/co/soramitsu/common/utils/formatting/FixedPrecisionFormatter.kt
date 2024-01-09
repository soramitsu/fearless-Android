package jp.co.soramitsu.common.utils.formatting

import jp.co.soramitsu.common.utils.decimalFormatterFor
import jp.co.soramitsu.common.utils.patternWith
import java.math.BigDecimal

class FixedPrecisionFormatter(
    private val precision: Int
) : NumberFormatter {

    override fun format(number: BigDecimal): String {
        return decimalFormatterFor(patternWith(precision)).format(number)
    }
}
