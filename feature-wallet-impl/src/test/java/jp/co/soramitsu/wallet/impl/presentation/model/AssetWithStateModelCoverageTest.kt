package jp.co.soramitsu.wallet.impl.presentation.model

import jp.co.soramitsu.wallet.impl.WalletTestModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A row keeps showing its updating state until the chain, balance and rate sources each report they are done. */
class AssetWithStateModelCoverageTest {

    @Test
    fun unknownSourcesCountAsStillUpdating() {
        val unknown = AssetUpdateState(rateUpdate = null, balanceUpdate = null, chainUpdate = null, isTokenFiatChanged = null)

        assertTrue(unknown.isBalanceUpdating)
        assertTrue(unknown.isRateUpdating)
        assertTrue(unknown.isFiatUpdating)
    }

    @Test
    fun settledSourcesStopEveryIndicator() {
        val settled = AssetUpdateState(rateUpdate = false, balanceUpdate = false, chainUpdate = false, isTokenFiatChanged = false)

        assertFalse(settled.isBalanceUpdating)
        assertFalse(settled.isRateUpdating)
        assertFalse(settled.isFiatUpdating)
    }

    @Test
    fun eachIndicatorFollowsItsOwnSources() {
        val rateOnly = AssetUpdateState(rateUpdate = true, balanceUpdate = false, chainUpdate = false, isTokenFiatChanged = true)
        assertFalse(rateOnly.isBalanceUpdating)
        assertTrue(rateOnly.isRateUpdating)
        assertTrue(rateOnly.isFiatUpdating)

        val balanceOnly = AssetUpdateState(rateUpdate = false, balanceUpdate = true, chainUpdate = false, isTokenFiatChanged = false)
        assertTrue(balanceOnly.isBalanceUpdating)
        assertFalse(balanceOnly.isRateUpdating)
        assertTrue(balanceOnly.isFiatUpdating)

        val chainOnly = AssetUpdateState(rateUpdate = false, balanceUpdate = false, chainUpdate = true, isTokenFiatChanged = false)
        assertTrue(chainOnly.isBalanceUpdating)
        assertTrue(chainOnly.isRateUpdating)
        assertTrue(chainOnly.isFiatUpdating)
    }

    @Test
    fun aRowPairsItsAssetWithItsUpdateState() {
        val asset = AssetModel(WalletTestModels.fundedAsset())
        val settled = AssetUpdateState(rateUpdate = false, balanceUpdate = false, chainUpdate = false, isTokenFiatChanged = false)
        val row = AssetWithStateModel(asset = asset, state = settled)

        assertSame(asset, row.asset)
        assertEquals(row, AssetWithStateModel(asset, settled.copy()))
        val updating = row.copy(state = settled.copy(balanceUpdate = true))
        assertNotEquals(row, updating)
        assertTrue(updating.state.isBalanceUpdating)
    }
}
