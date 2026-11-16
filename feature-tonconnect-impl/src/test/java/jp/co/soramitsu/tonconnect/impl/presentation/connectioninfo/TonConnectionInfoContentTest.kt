package jp.co.soramitsu.tonconnect.impl.presentation.connectioninfo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.tonconnect.api.model.DappModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class TonConnectionInfoContentTest {
    @get:Rule val compose = createComposeRule()
    private val callback = mock<TonConnectionInfoScreenInterface>()

    @Before
    fun advanceClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun connectionDetailsDisplayBoundWalletAndAllowDisconnect() {
        val state = TonConnectionInfoViewState(
            DappModel("client", listOf("-239"), "Bird Swap", "https://swap.example", null, null, null, 1),
            SelectorState("Network", "TON mainnet", null),
            WalletNameItemViewState(1, "Connected wallet", R.drawable.ic_wallet, true)
        )
        compose.setContent { FearlessAppTheme { TonConnectionInfoContent(state, callback) } }
        listOf("Bird Swap", "https://swap.example", "TON mainnet", "Connected wallet").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("Disconnect").performClick()
        verify(callback).onDisconnectClick()
    }

    @Test
    fun defaultStateOmitsWalletAndNetworks() {
        compose.setContent { FearlessAppTheme { TonConnectionInfoContent(TonConnectionInfoViewState.default, callback) } }
        compose.onNodeWithText("Connected wallet").assertDoesNotExist()
        compose.onNodeWithText("Network").assertDoesNotExist()
        compose.onNodeWithText("Disconnect").assertIsDisplayed()
    }
}
