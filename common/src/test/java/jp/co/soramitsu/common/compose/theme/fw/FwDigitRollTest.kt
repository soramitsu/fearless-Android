package jp.co.soramitsu.common.compose.theme.fw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FwDigitRollTest {
    @Test fun firstValueDoesNotRoll() {
        val plan = fwDigitRollPlan(null, "$12,814.22")
        assertTrue(plan.unchanged)
        assertEquals(0, plan.direction)
        assertTrue(plan.columns.none { it.rolls || it.changed })
        assertEquals("$12,814.22", plan.text)
    }

    @Test fun sameLengthRollsOnlyChangedDigitsAndTintsFromTheFirstChange() {
        val plan = fwDigitRollPlan("$12,814.22", "$13,028.30")
        assertEquals("$13,028.30", plan.text)
        assertEquals(2, plan.changedFrom)
        assertEquals(1, plan.direction)
        val rolling = plan.columns.mapIndexedNotNull { index, column -> index.takeIf { column.rolls } }
        assertEquals(listOf(2, 4, 5, 6, 8, 9), rolling)
        assertFalse(plan.columns[3].changed) // the thousands separator stays put
        assertEquals('2', plan.columns[2].previous)
    }

    @Test fun decreasesRollDown() {
        val plan = fwDigitRollPlan("$13,028.30", "$12,814.22")
        assertEquals(-1, plan.direction)
    }

    @Test fun lengthChangesAlignFromTheRightSoDecimalsStayPut() {
        val plan = fwDigitRollPlan("$999.99", "$1,000.01")
        assertEquals("$1,000.01", plan.text)
        assertEquals(1, plan.direction)
        assertEquals(0, plan.changedFrom)
        // The cents column was '9' and is now '1': it rolls in place.
        assertEquals('9', plan.columns.last().previous)
        assertTrue(plan.columns.last().rolls)
        // New leading characters have nothing to roll from.
        assertNull(plan.columns[0].previous)
        assertNull(plan.columns[1].previous)
        assertEquals('$', plan.columns[2].previous)
        val shorter = fwDigitRollPlan("$1,000.01", "$999.99")
        assertEquals(-1, shorter.direction)
        assertEquals("$999.99", shorter.text)
    }

    @Test fun nonDigitChangesSwapInsteadOfRolling() {
        val plan = fwDigitRollPlan("12.50 DOT", "12.50 KSM")
        assertTrue(plan.columns.filter { it.changed }.none { it.rolls })
        assertEquals(0, plan.direction)
    }

    @Test fun identicalValuesAreUnchanged() {
        val plan = fwDigitRollPlan("$1.00", "$1.00")
        assertTrue(plan.unchanged)
        assertEquals(0, plan.direction)
    }

    @Test fun amountsCompareOnlyWhenTheyShareAFormat() {
        assertEquals(-1, fwCompareAmounts("$999.99", "$1,000.01"))
        assertEquals(1, fwCompareAmounts("12 814,22 ₽", "12 813,99 ₽"))
        assertEquals(0, fwCompareAmounts("$0.10", "$0.10"))
        assertEquals(1, fwCompareAmounts("0.0812 BTC", "0.0811 BTC"))
        assertNull(fwCompareAmounts("$1.5", "$1.50"))
        assertNull(fwCompareAmounts("—", "$1.00"))
        assertNull(fwCompareAmounts("-$1.00", "$1.00"))
        assertEquals(-1, fwCompareAmounts("$0.09", "$1.00"))
    }
}
