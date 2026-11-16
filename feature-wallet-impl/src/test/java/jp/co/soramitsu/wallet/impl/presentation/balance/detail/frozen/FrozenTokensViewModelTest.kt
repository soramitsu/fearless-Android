package jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen

import androidx.lifecycle.SavedStateHandle
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class FrozenTokensViewModelTest {

    @Test
    fun stateCarriesTheFrozenPartsFromTheArguments() {
        val payload = FrozenAssetPayload(locked = BigDecimal.ONE, staked = BigDecimal.TEN, assetSymbol = "DOT")

        val viewModel = FrozenTokensViewModel(SavedStateHandle(mapOf(FROZEN_ASSET_PAYLOAD to payload)))

        assertEquals(payload, viewModel.state.value.frozenAssetPayload)
        assertEquals(BigDecimal.TEN, viewModel.state.value.frozenAssetPayload.staked)
    }
}
