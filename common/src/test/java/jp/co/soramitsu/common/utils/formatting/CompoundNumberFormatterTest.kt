package jp.co.soramitsu.common.utils.formatting

import jp.co.soramitsu.common.utils.defaultNumberFormatter
import org.junit.Test

class CompoundNumberFormatterTest {

    private val formatter = defaultNumberFormatter()

    @Test
    fun `should format all cases`() {
        testFormatter(formatter, "0.00000001", "0.000000011676979")
        testFormatter(formatter, "0.00002", "0.000021676979")
        testFormatter(formatter, "0.315", "0.315000041811")
        testFormatter(formatter, "0.99999", "0.99999999999")
        testFormatter(formatter, "999.99999", "999.99999999")
        testFormatter(formatter, "888,888.12", "888888.1234")
        testFormatter(formatter, "1.24M", "1243000")
        testFormatter(formatter, "1.24M", "1243011")
        testFormatter(formatter, "100.04B", "100041000000")
        testFormatter(formatter, "1T", "1001000000000")
        testFormatter(formatter, "1,001T", "1001000000000000")
    }
}
