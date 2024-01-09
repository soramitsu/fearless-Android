package jp.co.soramitsu.common.utils.formatting

import jp.co.soramitsu.common.utils.cryptoShortAbbreviatedFormatter
import org.junit.Test

class CryptoListAbbreviatedFormatterTest {

    private val formatter = cryptoShortAbbreviatedFormatter()

    @Test
    fun `should format all cases`() {
        testFormatter(formatter, "0", "0.000000011676979")
        testFormatter(formatter, "0", "0.000021676979")
        testFormatter(formatter, "0.315", "0.315000041811")
        testFormatter(formatter, "0.999", "0.99999999999")
        testFormatter(formatter, "999.999", "999.99999999")
        testFormatter(formatter, "1M", "1000000")
        testFormatter(formatter, "888,888.123", "888888.1234")
        testFormatter(formatter, "1.243M", "1243000")
        testFormatter(formatter, "1.243M", "1243011")
        testFormatter(formatter, "100.041B", "100041000000")
        testFormatter(formatter, "1.001T", "1001000000000")
        testFormatter(formatter, "1,001T", "1001000000000000")
        testFormatter(formatter, "1,001.001T", "1001001000111000")
    }
}
