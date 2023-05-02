package jp.co.soramitsu.common.utils.formatting

import org.junit.Test

class FullPrecisionFormatterTest {

    private val formatter = FullPrecisionFormatter()

    @Test
    fun `test format`() {
        testFormatter(formatter, "1.2345", "1.2345")
        testFormatter(formatter, "1.2", "1.2")
        testFormatter(formatter, "1.23", "1.23")
        testFormatter(formatter, "1", "1")
        testFormatter(formatter, "1", "1.00")
        testFormatter(formatter, "1.007", "1.007")
        testFormatter(formatter, "123,456", "123456")
        testFormatter(formatter, "0.06", "0.06")
        testFormatter(formatter, "0.067", "0.067")
        testFormatter(formatter, "0.123", "0.123")
        testFormatter(formatter, "123,234.123", "123234.123")
        testFormatter(formatter, "123,234.123456789012345689", "123234.123456789012345689")
    }
}
