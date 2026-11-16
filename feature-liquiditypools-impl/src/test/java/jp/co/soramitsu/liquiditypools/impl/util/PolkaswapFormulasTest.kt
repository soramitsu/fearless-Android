package jp.co.soramitsu.liquiditypools.impl.util

import jp.co.soramitsu.polkaswap.api.models.WithDesired
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PolkaswapFormulasTest {

    @Test
    fun `pooled value and share follow the provider balance`() {
        assertAmount("100", PolkaswapFormulas.calculatePooledValue("1000".bd, "30".bd, "300".bd))
        assertAmount("33.33", PolkaswapFormulas.calculatePooledValue("100".bd, "1".bd, "3".bd, 2))
        assertEquals(100.0, PolkaswapFormulas.calculateShareOfPoolFromAmount("5.0".bd, "5".bd), 0.0)
        assertEquals(25.0, PolkaswapFormulas.calculateShareOfPoolFromAmount("1".bd, "4".bd), 0.0)
    }

    @Test
    fun `add amount keeps the pool ratio in both directions`() {
        assertAmount("6", PolkaswapFormulas.calculateAddLiquidityAmount("2".bd, "1000".bd, "3000".bd, 18, 18, WithDesired.INPUT))
        assertAmount("2", PolkaswapFormulas.calculateAddLiquidityAmount("6".bd, "1000".bd, "3000".bd, 18, 18, WithDesired.OUTPUT))
        assertAmount("0", PolkaswapFormulas.calculateAddLiquidityAmount("6".bd, "0".bd, "3000".bd, 18, 18, WithDesired.INPUT))
    }

    @Test
    fun `share estimates after adding or removing liquidity`() {
        assertAmount("20", PolkaswapFormulas.estimateAddingShareOfPool("10".bd, "10".bd, "90".bd))
        assertAmount("20", PolkaswapFormulas.estimateRemovingShareOfPool("10".bd, "30".bd, "110".bd))
        assertAmount("0", PolkaswapFormulas.estimateRemovingShareOfPool("10".bd, "30".bd, "10".bd))
    }

    @Test
    fun `slippage, rates, marker amount and bonus yield`() {
        assertAmount("99.5", PolkaswapFormulas.calculateMinAmount("100".bd, 0.5))
        assertAmount("2", PolkaswapFormulas.calculateTokenPerTokenRate("6".bd, "3".bd))
        assertAmount("5", PolkaswapFormulas.calculateMarkerAssetDesired("10".bd, "100".bd, "50".bd))
        assertEquals(25.0, PolkaswapFormulas.calculateStrategicBonusAPY(0.25)!!, 1e-12)
        assertNull(PolkaswapFormulas.calculateStrategicBonusAPY(null))
    }

    @Test
    fun `percentage and proportional amounts`() {
        val whole = "200".bd
        assertSame(whole, PolkaswapFormulas.calculateAmountByPercentage(whole, 100.0, 18))
        assertAmount("50", PolkaswapFormulas.calculateAmountByPercentage(whole, 25.0, 18))
        assertAmount("6", PolkaswapFormulas.calculateOneAmountFromAnother("2".bd, "10".bd, "30".bd))
        assertAmount("0", PolkaswapFormulas.calculateOneAmountFromAnother("2".bd, "0".bd, "30".bd))
    }

    private val String.bd get() = BigDecimal(this)

    private fun assertAmount(expected: String, actual: BigDecimal) {
        assertTrue("expected $expected but was $actual", BigDecimal(expected).compareTo(actual) == 0)
    }
}
