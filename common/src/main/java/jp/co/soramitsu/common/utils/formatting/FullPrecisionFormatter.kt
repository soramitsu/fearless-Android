package jp.co.soramitsu.common.utils.formatting

import jp.co.soramitsu.common.utils.decimalFormatterFor
import jp.co.soramitsu.common.utils.patternWith
import java.math.BigDecimal

class FullPrecisionFormatter : NumberFormatter {

    override fun format(number: BigDecimal): String {
        val requiredPrecision = number.scale()

        return decimalFormatterFor(patternWith(requiredPrecision)).format(number)
    }
}
