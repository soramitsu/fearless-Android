package jp.co.soramitsu.wallet.impl.presentation

import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.areAllPortfolioAssetsHidden
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioEmptyStateTest {
    @Test
    fun `new wallet with no loaded assets never claims the user hid assets`() {
        assertFalse(areAllPortfolioAssetsHidden(emptyList()))
        assertFalse(AssetsLoadingState.Loaded(emptyList()).allAssetsHidden)
    }

    @Test
    fun `explicitly disabled assets in the selected scope retain hidden-assets explanation`() {
        assertTrue(areAllPortfolioAssetsHidden(listOf(false, false)))
    }

    @Test
    fun `an enabled asset filtered from presentation does not imply all assets were hidden`() {
        assertFalse(areAllPortfolioAssetsHidden(listOf(false, true)))
        assertFalse(areAllPortfolioAssetsHidden(listOf(true)))
    }

    @Test
    fun `unknown visibility never implies a user hide action`() {
        assertFalse(areAllPortfolioAssetsHidden(listOf(null)))
        assertFalse(areAllPortfolioAssetsHidden(listOf(false, null)))
    }
}
