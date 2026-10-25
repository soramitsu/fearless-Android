package jp.co.soramitsu.wallet.api.data.mappers

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class AssetSelectorAvailabilityTest {
    @Test fun unknownAmountDisplaysUnavailableWithoutNumericZero() {
        val asset = mockk<Asset>(relaxed = true) { every { availableForStaking } returns null }
        val resources = mockk<ResourceManager> {
            every { getString(jp.co.soramitsu.common.R.string.portfolio_balance_unavailable) } returns "Balance unavailable"
        }
        val model = mapAssetToAssetModel(asset, resources, patternId = null)
        assertEquals("Balance unavailable", model.assetBalance)
    }

    @Test fun amountIsCapturedOnceForCryptoAndFiatDisplay() {
        val asset = mockk<Asset>(relaxed = true) {
            every { token.configuration.symbol } returns "UNIT"
            every { token.fiatAmount(BigDecimal.TEN) } returns null
        }
        var reads = 0
        val model = mapAssetToAssetModel(asset, mockk(), retrieveAmount = {
            reads++
            if (reads == 1) BigDecimal.TEN else error("Must retain the captured amount")
        }, patternId = null)
        assertEquals(1, reads)
        assertEquals("10 UNIT", model.assetBalance)
    }
}
