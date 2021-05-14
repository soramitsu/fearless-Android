package jp.co.soramitsu.common.utils.formatting

import jp.co.soramitsu.common.utils.decimalFormatterFor
import jp.co.soramitsu.common.utils.patternWith
import java.math.BigDecimal

class FixedPrecisionFormatter(
    private val precision: Int
) : NumberFormatter {

    private val delegate = decimalFormatterFor(patternWith(precision))

    override fun format(number: BigDecimal): String {
        return delegate.format(number)
    }
}
