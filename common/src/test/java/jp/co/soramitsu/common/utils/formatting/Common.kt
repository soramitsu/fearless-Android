package jp.co.soramitsu.common.utils.formatting

import org.junit.Assert
import java.math.BigDecimal

fun testFormatter(formatter: NumberFormatter, expected: String, unformattedValue: String) {
    Assert.assertEquals(expected, formatter.format(BigDecimal(unformattedValue)))
}
