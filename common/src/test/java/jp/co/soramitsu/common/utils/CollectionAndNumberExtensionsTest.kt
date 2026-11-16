package jp.co.soramitsu.common.utils

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionAndNumberExtensionsTest {
    @Test
    fun shouldConvertFractionsAndMultiplyExactFiatRates() {
        assertEquals(BigDecimal("12.50"), BigDecimal("0.125").fractionToPercentage().stripTrailingZeros().setScale(2))
        assertEquals(BigDecimal("0.125"), BigDecimal("12.500").percentageToFraction())
        assertEquals(BigDecimal("7.50"), BigDecimal("2.5").applyFiatRate(BigDecimal("3.0")))
        assertNull(BigDecimal.ONE.applyFiatRate(null))
        assertTrue(BigDecimal.ZERO.isNonNegative)
        assertTrue(BigDecimal("-1").isNegative)
        assertFalse(BigDecimal.ONE.isNegative)
        assertEquals(2L, 172800000L.daysFromMillis())
    }

    @Test
    fun shouldDistinguishMissingZeroPositiveAndNegativeNumbers() {
        val missingInt: BigInteger? = null
        val missingDecimal: BigDecimal? = null
        assertEquals(BigInteger.ZERO, missingInt.orZero())
        assertEquals(BigDecimal.ZERO, missingDecimal.orZero())
        assertFalse(missingInt.isZero())
        assertFalse(missingDecimal.isZero())
        assertTrue(missingInt.isNotZero())
        assertTrue(missingDecimal.isNotZero())
        assertTrue(BigInteger.ZERO.isZero())
        assertTrue(BigDecimal("0.00").isZero())
        assertFalse(BigInteger.ZERO.isNotZero())
        assertFalse(BigDecimal.ZERO.isNotZero())
        assertTrue(BigInteger.TWO.greaterThen(BigInteger.ONE))
        assertTrue(BigDecimal.TEN.greaterThen(BigDecimal.ONE))
        assertTrue(BigInteger.ONE.greaterThanOrEquals(BigInteger.ONE))
        assertTrue(BigInteger.TWO.greaterThanOrEquals(BigInteger.ONE))
        assertFalse(missingInt.greaterThanOrEquals(BigInteger.ZERO))
        assertTrue(BigInteger.ZERO.lessThan(BigInteger.ONE))
        assertTrue(BigInteger.ONE.lessThanOrEquals(BigInteger.ONE))
        assertTrue(BigInteger.ZERO.lessThanOrEquals(BigInteger.ONE))
        assertFalse(missingInt.lessThan(BigInteger.ONE))
        assertNull(BigInteger.ZERO.positiveOrNull())
        assertEquals(BigInteger.ONE, BigInteger.ONE.positiveOrNull())
        assertEquals(BigInteger.ONE, BigInteger.ONE.orZero())
        assertEquals(BigDecimal.ONE, BigDecimal.ONE.orZero())
    }

    @Test
    fun shouldReverseMapsCycleListsAndCalculateMediansWithoutMutation() {
        assertEquals(mapOf(1 to "a", 2 to "b"), mapOf("a" to 1, "b" to 2).reversed())
        assertEquals(listOf("a", "b", "a", "b", "a"), listOf("a", "b").cycle().take(5).toList())
        assertEquals(listOf(3, 5, 7), generateLinearSequence(3, 2).take(3).toList())
        val values = listOf(9.0, 1.0, 3.0)
        assertEquals(3.0, values.median(), 0.0)
        assertEquals(listOf(9.0, 1.0, 3.0), values)
        assertEquals(2.5, listOf(4.0, 1.0).median(), 0.0)
        assertEquals(setOf("a", "b"), setOf("a").toggle("b"))
        assertEquals(setOf("a"), setOf("a", "b").toggle("b"))
        assertEquals("b", listOf("a", "b").second())
    }

    @Test
    fun shouldReadStreamsAndRequireResultValuesOrExceptions() {
        assertEquals("日本語", ByteArrayInputStream("日本語".toByteArray()).readText())
        assertNull("".nullIfEmpty())
        assertEquals("value", "value".nullIfEmpty())
        val failure = IllegalStateException("failed")
        assertSame(failure, Result.failure<String>(failure).requireException())
        assertEquals("ok", Result.success("ok").requireValue())
        assertEquals("failure", Result.failure<String>("failure").exceptionOrNull()?.message)
        assertEquals("0", String.ZERO)
        assertEquals('0', Char.ZERO)
    }
}
