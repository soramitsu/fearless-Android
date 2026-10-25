package jp.co.soramitsu.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class NumberExtensionsTest {

    @Test
    fun `converts every representative int boundary exactly`() {
        val values = listOf(
            Int.MIN_VALUE,
            Int.MIN_VALUE + 1,
            -1,
            0,
            1,
            Int.MAX_VALUE - 1,
            Int.MAX_VALUE
        )

        values.forEach { expected ->
            assertEquals(expected, BigInteger.valueOf(expected.toLong()).toIntExact())
        }
    }

    @Test
    fun `rejects every int overflow direction without truncation`() {
        val overflows = listOf(
            BigInteger.valueOf(Int.MIN_VALUE.toLong()) - BigInteger.ONE,
            BigInteger.valueOf(Int.MAX_VALUE.toLong()) + BigInteger.ONE,
            BigInteger.ONE.shiftLeft(4_096).negate(),
            BigInteger.ONE.shiftLeft(4_096)
        )

        overflows.forEach { value ->
            val error = assertThrows(ArithmeticException::class.java) {
                value.toIntExact()
            }

            assertEquals("BigInteger out of int range", error.message)
        }
    }

    @Test
    fun `converts every representative long boundary exactly`() {
        val values = listOf(
            Long.MIN_VALUE,
            Long.MIN_VALUE + 1,
            -1L,
            0L,
            1L,
            Long.MAX_VALUE - 1,
            Long.MAX_VALUE
        )

        values.forEach { expected ->
            assertEquals(expected, BigInteger.valueOf(expected).toLongExact())
        }
    }

    @Test
    fun `rejects every long overflow direction without truncation`() {
        val overflows = listOf(
            BigInteger.valueOf(Long.MIN_VALUE) - BigInteger.ONE,
            BigInteger.valueOf(Long.MAX_VALUE) + BigInteger.ONE,
            BigInteger.ONE.shiftLeft(4_096).negate(),
            BigInteger.ONE.shiftLeft(4_096)
        )

        overflows.forEach { value ->
            val error = assertThrows(ArithmeticException::class.java) {
                value.toLongExact()
            }

            assertEquals("BigInteger out of long range", error.message)
        }
    }
}
