package jp.co.soramitsu.walletconnect.impl.presentation.sessionproposal

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
class SessionProposalContentTest {
    @get:Rule val compose = createComposeRule()
    private val callback = mock<SessionProposalScreenInterface>()

    @Before
    fun advanceClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun proposalShowsOriginPermissionsAndForwardsNetworkAndWalletChoices() {
        val wallet = WalletNameItemViewState(1, "Signing wallet", R.drawable.ic_wallet, true)
        val state = SessionProposalViewState.default.copy(
            originHost = "swap.example",
            originWarning = "Unverified origin",
            requiredNetworksSelectorState = SelectorState("Required networks", "Ethereum", null),
            optionalNetworksSelectorState = SelectorState("Optional networks", "Polygon", null),
            requiredPermissions = InfoItemSetViewState("Required", listOf(InfoItemViewState("Method", "personal_sign"))),
            optionalPermissions = InfoItemSetViewState("Optional", listOf(InfoItemViewState("Event", "chainChanged"))),
            wallets = listOf(wallet)
        )
        compose.setContent { FearlessAppTheme { SessionProposalContent(state, callback) } }
        compose.onNodeWithText("swap.example").assertIsDisplayed()
        compose.onNodeWithText("Unverified origin").assertIsDisplayed()
        compose.onNodeWithText("Ethereum").performScrollTo().performClick()
        verify(callback).onRequiredNetworksClicked()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Polygon").performScrollTo().performClick()
        verify(callback).onOptionalNetworksClicked()
        compose.onNodeWithText("Review required permissions").performScrollTo().performClick()
        compose.onNodeWithText("personal_sign").performScrollTo().assertIsDisplayed()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Review optional permissions").performScrollTo().performClick()
        compose.onNodeWithText("chainChanged").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Signing wallet").performScrollTo().performClick()
        verify(callback).onWalletSelected(wallet)
    }

    @Test
    fun approvalAndRejectionReachCallbacks() {
        compose.setContent { FearlessAppTheme { SessionProposalContent(SessionProposalViewState.default, callback) } }
        compose.onNodeWithText("Approve").performClick()
        verify(callback).onApproveClick()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Reject").performClick()
        verify(callback).onRejectClicked()
    }

    @Test
    fun ongoingDecisionDisablesOppositeAction() {
        val state = mutableStateOf(SessionProposalViewState.default.copy(approving = true))
        compose.setContent { FearlessAppTheme { SessionProposalContent(state.value, callback) } }
        compose.onNodeWithText("Reject").assertIsNotEnabled().performClick()
        compose.runOnIdle { state.value = state.value.copy(approving = false, rejecting = true) }
        compose.onNodeWithText("Approve").assertIsNotEnabled().performClick()
        verifyNoInteractions(callback)
    }
}
