package jp.co.soramitsu.common.utils.formatting

import org.junit.Test

class FiatFormatterTest {

    private val formatter = FiatFormatter()

    @Test
    fun `test format`() {
        testFormatter(formatter, "1.23", "1.2345")
        testFormatter(formatter, "1.20", "1.2")
        testFormatter(formatter, "1.23", "1.23")
        testFormatter(formatter, "1", "1")
        testFormatter(formatter, "1", "1.00")
        testFormatter(formatter, "1", "1.007")
        testFormatter(formatter, "1.70", "1.7")
        testFormatter(formatter, "123,456", "123456")
        testFormatter(formatter, "0.06", "0.06")
        testFormatter(formatter, "0.06", "0.067")
        testFormatter(formatter, "0.12", "0.123")
        testFormatter(formatter, "123,234.12", "123234.123")
    }
}
