package jp.co.soramitsu.wallet.impl.domain.model

import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.SoraCardItemViewState
import jp.co.soramitsu.common.compose.component.SoraCardProgress
import jp.co.soramitsu.wallet.impl.presentation.balance.list.WalletAssetsState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.WalletState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class WalletStateTest {

    @Test
    fun `wallet test default`() {
        val s = WalletState.default
        assertEquals(null, s.scrollToTopEvent)
        assertEquals(null, s.scrollToBottomEvent)
        assertEquals(true, s.isBackedUp)
        assertEquals(false, s.hasNetworkIssues)
        assertEquals(
            SoraCardItemViewState(
                visible = true,
                success = false,
                iban = null,
                soraCardProgress = SoraCardProgress.START,
                loading = true,
            ), s.soraCardState
        )
        assertEquals(
            AssetBalanceViewState("", "", false, ChangeBalanceViewState("", "")),
            s.balance,
        )
        assertEquals(WalletAssetsState.Assets(emptyList(), isHideVisible = true), s.assetsState)
        assertEquals(
            MultiToggleButtonState(
                AssetType.Currencies,
                listOf(AssetType.Currencies, AssetType.NFTs)
            ), s.multiToggleButtonState
        )
    }
}
