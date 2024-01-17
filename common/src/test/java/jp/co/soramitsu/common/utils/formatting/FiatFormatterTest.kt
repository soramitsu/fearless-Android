package jp.co.soramitsu.common.utils.formatting

import java.util.Locale
import org.junit.Test

class FiatFormatterTest {

    private val formatter = FiatFormatter(Locale.US)
    private val formatterSmall = FiatSmallFormatter(Locale.US)

    @Test
    fun `test small fiat format`() {
        testFormatter(formatterSmall, "0.001234", "0.0012345678")
        testFormatter(formatterSmall, "0.0001234", "0.00012345678")
        testFormatter(formatterSmall, "0.000000000001004", "0.0000000000010045678")
        testFormatter(formatterSmall, "0.06", "0.06")
        testFormatter(formatterSmall, "0.067", "0.067")
        testFormatter(formatterSmall, "0.123", "0.123")
        testFormatter(formatterSmall, "120.1", "120.123")
        testFormatter(formatterSmall, "120,300,000", "120340034.123")
    }
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
