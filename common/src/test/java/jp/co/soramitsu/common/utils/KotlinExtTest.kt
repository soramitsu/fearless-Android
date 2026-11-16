package jp.co.soramitsu.common.utils

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinExtTest {

    @Test
    fun `should calculate median of single element list`() {
        val median = listOf(2.0).median()

        assertEquals(2.0, median, 0.0001)
    }

    @Test
    fun `should calculate median of odd sized list`() {
        val median = listOf(2.0, 2.0, 3.0, 4.0, 5.0).median()

        assertEquals(3.0, median, 0.0001)
    }

    @Test
    fun `should calculate median of even sized list`() {
        val median = listOf(2.0, 2.0, 4.0, 5.0).median()

        assertEquals(3.0, median, 0.0001)
    }

    @Test
    fun `percentage to fraction keeps the digits a percentage scale cannot hold`() {
        assertEquals(0, BigDecimal("0.125").compareTo(BigDecimal("12.5").percentageToFraction()))
        assertEquals(0, BigDecimal("0.05").compareTo(BigDecimal("5").percentageToFraction()))
        assertEquals(0, BigDecimal("0.0001").compareTo(BigDecimal("0.01").percentageToFraction()))
        assertEquals(0, BigDecimal.ONE.compareTo(BigDecimal("100").percentageToFraction()))
    }

    @Test
    fun `percentage to fraction and back is the identity`() {
        listOf("0", "5", "12.5", "33.333", "100", "250").map(::BigDecimal).forEach { percentage ->
            assertEquals(0, percentage.compareTo(percentage.percentageToFraction().fractionToPercentage()))
        }
    }
}
