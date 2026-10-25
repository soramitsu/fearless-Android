package jp.co.soramitsu.core.utils

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExactBigIntegerTest {
    @Test fun longSignedLimitsZeroAndSmallValuesRemainExact() {
        for (value in listOf(Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L, Long.MAX_VALUE - 1, Long.MAX_VALUE)) {
            assertEquals(value, BigInteger.valueOf(value).toLongExact())
        }
    }
    @Test fun intSignedLimitsZeroAndSmallValuesRemainExact() {
        for (value in listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -1, 0, 1, Int.MAX_VALUE - 1, Int.MAX_VALUE)) {
            assertEquals(value, BigInteger.valueOf(value.toLong()).toIntExact())
        }
    }
    @Test fun valuesImmediatelyOutsideLongRangeThrowWithoutWrapping() {
        for (value in listOf(BigInteger.valueOf(Long.MIN_VALUE) - BigInteger.ONE,
            BigInteger.valueOf(Long.MAX_VALUE) + BigInteger.ONE)) {
            assertThrows(ArithmeticException::class.java) { value.toLongExact() }
        }
    }
    @Test fun valuesImmediatelyOutsideIntRangeThrowWithoutWrapping() {
        for (value in listOf(BigInteger.valueOf(Int.MIN_VALUE.toLong()) - BigInteger.ONE,
            BigInteger.valueOf(Int.MAX_VALUE.toLong()) + BigInteger.ONE)) {
            assertThrows(ArithmeticException::class.java) { value.toIntExact() }
        }
    }
    @Test fun unsigned64MaximumAndHugeSignedInputsAreRejected() {
        for (value in listOf(BigInteger.ONE.shiftLeft(64) - BigInteger.ONE,
            BigInteger.ONE.shiftLeft(4096), BigInteger.ONE.shiftLeft(4096).negate())) {
            assertThrows(ArithmeticException::class.java) { value.toLongExact() }
            assertThrows(ArithmeticException::class.java) { value.toIntExact() }
        }
    }
    @Test fun deterministicWholeRangeLongInputsAndIntSubrangeAgreeWithDecimalParsing() {
        val random = java.util.Random(16726)
        repeat(1000) {
            val value = random.nextLong()
            assertEquals(value.toString().toLong(), BigInteger(value.toString()).toLongExact())
            val small = random.nextInt()
            assertEquals(small.toString().toInt(), BigInteger(small.toString()).toIntExact())
        }
    }
}
