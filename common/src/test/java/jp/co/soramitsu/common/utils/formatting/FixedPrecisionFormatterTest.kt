package jp.co.soramitsu.common.utils.formatting

import org.junit.Test

class FixedPrecisionFormatterTest {

    private val percentAmountFormatter = FixedPrecisionFormatter(2)
    private val cryptoListsAmountFormatter = FixedPrecisionFormatter(3)
    private val cryptoDetailsAmountFormatter = FixedPrecisionFormatter(8)

    @Test
    fun `test format`() {
        testFormatter(percentAmountFormatter, "1.23", "1.2345")
        testFormatter(percentAmountFormatter, "1.2", "1.2")
        testFormatter(percentAmountFormatter, "1.23", "1.23")
        testFormatter(percentAmountFormatter, "1", "1")
        testFormatter(percentAmountFormatter, "123,456", "123456")

        testFormatter(cryptoListsAmountFormatter, "1.234", "1.2345")
        testFormatter(cryptoListsAmountFormatter, "1.2", "1.2")
        testFormatter(cryptoListsAmountFormatter, "1.23", "1.23")
        testFormatter(cryptoListsAmountFormatter, "1", "1")
        testFormatter(cryptoListsAmountFormatter, "123,456", "123456")
        testFormatter(cryptoListsAmountFormatter, "123,456.567", "123456.567")
        testFormatter(cryptoListsAmountFormatter, "123,456.123", "123456.123456789")

        testFormatter(cryptoDetailsAmountFormatter, "1.2345", "1.2345")
        testFormatter(cryptoDetailsAmountFormatter, "1.2", "1.2")
        testFormatter(cryptoDetailsAmountFormatter, "1.23", "1.23")
        testFormatter(cryptoDetailsAmountFormatter, "1", "1")
        testFormatter(cryptoDetailsAmountFormatter, "123,456", "123456")
        testFormatter(cryptoDetailsAmountFormatter, "123,456.567", "123456.567")
        testFormatter(cryptoDetailsAmountFormatter, "123,456.12345678", "123456.123456789")
    }
}
