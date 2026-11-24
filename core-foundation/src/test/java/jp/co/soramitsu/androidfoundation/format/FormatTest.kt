package jp.co.soramitsu.androidfoundation.format

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    @Test
    fun nullAndScaledZeroHaveTheSameZeroSemantics() {
        assertTrue((null as BigDecimal?).isZero())
        assertTrue(BigDecimal("0.000").isZero())
        assertFalse(BigDecimal("-0.01").isZero())
        assertTrue((null as BigInteger?).isZero())
        assertTrue(BigInteger.ZERO.isZero())
        assertFalse(BigInteger.ONE.isZero())
        assertEquals(BigDecimal.ZERO, (null as BigDecimal?).orZero())
        assertEquals(BigInteger.ZERO, (null as BigInteger?).orZero())
        val decimal = BigDecimal("12.30")
        val integer = BigInteger("123")
        assertSame(decimal, decimal.orZero())
        assertSame(integer, integer.orZero())
    }

    @Test
    fun balancesSortDescendingWithNullLast() {
        val balances = listOf(null, BigDecimal("2"), BigDecimal("0"), BigDecimal("2.00"))
        assertEquals(
            listOf(BigDecimal("2"), BigDecimal("2.00"), BigDecimal.ZERO, null),
            balances.sortedWith(::compareNullDesc)
        )
        assertEquals(0, compareNullDesc(null, null))
        assertTrue(compareNullDesc(null, BigDecimal.ONE) > 0)
        assertTrue(compareNullDesc(BigDecimal.ONE, null) < 0)
    }

    @Test
    fun balanceConversionIsExactAndTruncatesSubunitRemaindersTowardZero() {
        val atomic = BigInteger("123456789012345678901234567890")
        val display = mapBalance(atomic, 18)
        assertEquals(BigDecimal("123456789012.345678901234567890"), display)
        assertEquals(atomic, mapBalance(display, 18))
        assertEquals(BigInteger("123"), mapBalance(BigDecimal("1.239"), 2))
        assertEquals(BigInteger("-123"), mapBalance(BigDecimal("-1.239"), 2))
        assertEquals(BigInteger.ONE, mapBalance(BigDecimal.ONE, 0))
    }

    @Test
    fun divisionRoundsHalfUpAndRejectsZeroUnlessSafeDivisionIsRequested() {
        assertEquals(BigDecimal("0.333333333333333333"), BigDecimal.ONE.divideBy(BigDecimal(3)))
        assertEquals(BigDecimal("0.13"), BigDecimal.ONE.divideBy(BigDecimal(8), 2))
        assertEquals(BigDecimal("-0.13"), BigDecimal.ONE.negate().divideBy(BigDecimal(8), 2))
        assertEquals(BigDecimal("2.5"), BigDecimal.TEN.safeDivide(BigDecimal(4)))
        assertEquals(BigDecimal("0.13"), BigDecimal.ONE.safeDivide(BigDecimal(8), 2))
        assertEquals(BigDecimal.ZERO, BigDecimal.TEN.safeDivide(BigDecimal("0.00")))
        assertThrows(IllegalArgumentException::class.java) { BigDecimal.ONE.divideBy(BigDecimal.ZERO) }
        assertTrue(BigDecimal("1.00").equalTo(BigDecimal.ONE))
        assertFalse(BigDecimal("1.01").equalTo(BigDecimal.ONE))
    }

    @Test
    fun hexPrefixAndSafeCastPreserveAlreadyValidValues() {
        assertEquals("0x1234", "1234".addHexPrefix())
        assertEquals("0x1234", "0x1234".addHexPrefix())
        assertEquals("0x", "".addHexPrefix())
        assertEquals("text", ("text" as Any).safeCast<String>())
        assertNull(123.safeCast<String>())
        assertNull(null.safeCast<String>())
        assertEquals(BigDecimal(100), Big100)
        assertEquals("first" to "second", StringPair("first", "second").let { it.first to it.second })
    }
}
