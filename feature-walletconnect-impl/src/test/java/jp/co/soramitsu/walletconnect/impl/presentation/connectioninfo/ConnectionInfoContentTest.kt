package jp.co.soramitsu.walletconnect.impl.presentation.connectioninfo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.InfoItemSetViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
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
class ConnectionInfoContentTest {
    @get:Rule val compose = createComposeRule()
    private val callback = mock<ConnectionInfoScreenInterface>()

    @Before
    fun advanceClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun connectionShowsPermissionsWalletAndExpiryAndAllowsDisconnect() {
        val state = ConnectInfoViewState(
            InfoItemViewState("Bird Swap", "swap.example"),
            InfoItemSetViewState("Ethereum", listOf(InfoItemViewState("Methods", "personal_sign"))),
            listOf(WalletNameItemViewState(1, "Connected wallet", R.drawable.ic_wallet, false)),
            "16 November 2026"
        )
        compose.setContent { FearlessAppTheme { ConnectionInfoContent(state, callback) } }
        listOf("Bird Swap", "swap.example", "Ethereum", "personal_sign", "Connected wallet", "16 November 2026").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Disconnect").performClick()
        verify(callback).onDisconnectClick()
    }
}
