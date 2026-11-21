package jp.co.soramitsu.polkaswap.api.models

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketTest {

    @Test fun nodeSourceNamesMapToKnownMarketsOnly() {
        assertEquals(
            listOf(Market.TBC, Market.XYK),
            listOf("XYKPool", "MulticollateralBondingCurvePool", "XSTPool").toMarkets()
        )
        assertEquals(emptyList<Market>(), emptyList<String>().toMarkets())
    }

    @Test fun smartRoutingIsNeverSentAsASource() {
        assertEquals(listOf("XYKPool"), listOf(Market.SMART, Market.XYK).backStrings())
        assertEquals(emptyList<String>(), listOf(Market.SMART).backStrings())
    }

    @Test fun onlyAnExplicitMarketRestrictsTheRoute() {
        assertEquals("Disabled", emptyList<Market>().toFilters())
        assertEquals("Disabled", listOf(Market.SMART, Market.TBC).toFilters())
        assertEquals("AllowSelected", listOf(Market.TBC).toFilters())
    }

    @Test fun desiredSideNamesTheSwapVariant() {
        assertEquals("WithDesiredInput", WithDesired.INPUT.backString)
        assertEquals("WithDesiredOutput", WithDesired.OUTPUT.backString)
    }
}
