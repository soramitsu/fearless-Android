package jp.co.soramitsu.common.utils.formatting

import org.junit.Test

class DynamicPrecisionFormatterTest {
    private val formatter = DynamicPrecisionFormatter(2)

    @Test
    fun `test format`() {
        testFormatter(formatter, "0.01", "0.012")
        testFormatter(formatter, "0.12", "0.123")
        testFormatter(formatter, "0.00001", "0.00001")
        testFormatter(formatter, "0.00001", "0.0000123")
    }
}
