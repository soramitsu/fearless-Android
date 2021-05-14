package jp.co.soramitsu.common.utils.formatting

import org.junit.Test

class FixedPrecisionFormatterTest {

    private val formatter = FixedPrecisionFormatter(2)

    @Test
    fun `test format`() {
        testFormatter(formatter, "1.23", "1.2345")
        testFormatter(formatter, "1.2", "1.2")
        testFormatter(formatter, "1.23", "1.23")
        testFormatter(formatter, "1", "1")
        testFormatter(formatter, "123,456", "123456")
    }
}
