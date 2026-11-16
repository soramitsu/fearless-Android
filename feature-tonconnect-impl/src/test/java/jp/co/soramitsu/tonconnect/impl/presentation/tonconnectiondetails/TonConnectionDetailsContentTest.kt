package jp.co.soramitsu.tonconnect.impl.presentation.tonconnectiondetails

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.InfoItemSetViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class TonConnectionDetailsContentTest {
    @get:Rule val compose = createComposeRule()
    private val callback = mock<TonConnectionDetailsScreenInterface>()

    @Before
    fun advanceClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    @OptIn(androidx.compose.runtime.InternalComposeApi::class)
    fun rendersTonConnectionDetailsPreviewWithReadableContent() {
        val preview = Class.forName("jp.co.soramitsu.tonconnect.impl.presentation.tonconnectiondetails.TonConnectionDetailsContentKt")
            .getDeclaredMethod("TonConnectionDetailsPreview", androidx.compose.runtime.Composer::class.java, Int::class.javaPrimitiveType)
        preview.isAccessible = true
        compose.setContent {
            FearlessAppTheme {
                preview.invoke(null, androidx.compose.runtime.currentComposer, 0)
            }
        }
        compose.onNodeWithText("blueprint").assertExists()
        compose.onNodeWithText("Telegram mainnet").assertExists()
    }

    @Test
    fun proposalShowsDappNetworkAndWalletAndForwardsDecisions() {
        val wallet = WalletNameItemViewState(1, "My TON wallet", R.drawable.ic_wallet, true)
        val state = TonConnectionDetailsViewState.default.copy(
            appInfo = AppEntity("https://swap.example", "Bird Swap", "", null, null),
            requiredNetworksSelectorState = SelectorState("Network", "TON mainnet", null),
            reviewDappInfo = InfoItemSetViewState("Permissions", listOf(InfoItemViewState("Methods", "sendTransaction"))),
            wallets = listOf(wallet)
        )
        compose.setContent { FearlessAppTheme { TonConnectionDetailsContent(state, callback) } }
        compose.onNodeWithText("Bird Swap").assertIsDisplayed()
        compose.onNodeWithText("TON mainnet").assertIsDisplayed()
        compose.onNodeWithText("My TON wallet").performScrollTo().performClick()
        verify(callback).onWalletSelected(wallet)
        compose.onNodeWithText("Approve").performClick()
        verify(callback).onApproveClick()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Reject").performClick()
        verify(callback).onRejectClicked()
    }

    @Test
    fun inFlightDecisionDisablesTheOppositeAction() {
        val state = mutableStateOf(TonConnectionDetailsViewState.default.copy(approving = true))
        compose.setContent { FearlessAppTheme { TonConnectionDetailsContent(state.value, callback) } }
        compose.onNodeWithText("Reject").assertIsNotEnabled().performClick()
        compose.runOnIdle { state.value = state.value.copy(approving = false, rejecting = true) }
        compose.onNodeWithText("Approve").assertIsNotEnabled().performClick()
        verifyNoInteractions(callback)
    }
}
