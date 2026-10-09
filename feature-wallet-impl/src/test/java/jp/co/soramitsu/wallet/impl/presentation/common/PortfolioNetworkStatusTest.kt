package jp.co.soramitsu.wallet.impl.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortfolioNetworkStatusTest {
    @Test
    fun successfulBalancesNeedNoStatus() {
        assertNull(portfolioNetworkStatus(100L, isStale = false, errorMessage = null))
        assertNull(portfolioNetworkStatus(100L, isStale = false, errorMessage = " "))
    }

    @Test
    fun errorRemainsVisibleWithOrWithoutPreviouslyLoadedBalances() {
        for (lastSuccess in listOf(null, 100L)) {
            assertEquals(
                PortfolioNetworkStatus.Failed,
                portfolioNetworkStatus(lastSuccess, isStale = false, errorMessage = "endpoint unavailable")
            )
        }
    }

    @Test
    fun staleBalancesWarnWithoutRepeatingTheLastUpdateTime() {
        assertEquals(PortfolioNetworkStatus.Outdated, portfolioNetworkStatus(100L, isStale = true, errorMessage = null))
    }

    @Test
    fun failedInitialScanDoesNotImplyBalancesAreAvailable() {
        assertEquals(PortfolioNetworkStatus.Failed, portfolioNetworkStatus(null, isStale = true, errorMessage = null))
    }

    @Test
    fun missingFirstScanRemainsVisible() {
        assertEquals(PortfolioNetworkStatus.NotLoaded, portfolioNetworkStatus(null, isStale = false, errorMessage = null))
    }
}
