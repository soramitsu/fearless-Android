package jp.co.soramitsu.common.utils.formatting

import jp.co.soramitsu.common.utils.cryptoDetailAbbreviatedFormatter
import org.junit.Test

class CryptoDetailsAbbreviatedFormatterTest {

    private val formatter = cryptoDetailAbbreviatedFormatter()

    @Test
    fun `should format all cases`() {
        testFormatter(formatter, "0.00000001", "0.000000011676979")
        testFormatter(formatter, "0.00002167", "0.000021676979")
        testFormatter(formatter, "0.31500004", "0.315000041811")
        testFormatter(formatter, "0.99999999", "0.99999999999")
        testFormatter(formatter, "999.99999999", "999.99999999")
        testFormatter(formatter, "1M", "1000000")
        testFormatter(formatter, "888,888.123", "888888.1234")
        testFormatter(formatter, "1.243M", "1243000")
        testFormatter(formatter, "1.243011M", "1243011")
        testFormatter(formatter, "100.041B", "100041000000")
        testFormatter(formatter, "1.001T", "1001000000000")
        testFormatter(formatter, "1,001T", "1001000000000000")
        testFormatter(formatter, "1,001.001T", "1001001000111000")
    }
}
