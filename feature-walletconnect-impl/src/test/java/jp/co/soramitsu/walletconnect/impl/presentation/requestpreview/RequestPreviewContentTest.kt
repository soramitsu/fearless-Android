package jp.co.soramitsu.walletconnect.impl.presentation.requestpreview

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import jp.co.soramitsu.common.compose.component.SigningDetailViewState
import jp.co.soramitsu.common.compose.component.SigningSummarySection
import jp.co.soramitsu.common.compose.component.SigningSummaryViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
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
class RequestPreviewContentTest {
    @get:Rule val compose = createComposeRule()
    private val callback = mock<RequestPreviewScreenInterface>()
    private val summary = SigningSummaryViewState(
        "Transfer", listOf(SigningSummarySection("Transfer details", listOf(SigningDetailViewState("Amount", "2 ETH")))),
        emptyList(), false
    )

    @Before
    fun advanceClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun decodedRequestShowsSummaryAndAllowsSigning() {
        val state = RequestPreviewViewState.default.copy(
            method = "eth_sendTransaction",
            wallet = RequestPreviewViewState.default.wallet.copy(title = "Signing wallet"),
            tableItems = listOf(TitleValueViewState("Network", "Ethereum")),
            summary = summary,
            originWarning = "Unverified origin"
        )
        compose.setContent { FearlessAppTheme { RequestPreviewContent(state, callback) } }
        listOf("eth_sendTransaction", "Signing wallet", "Ethereum", "2 ETH", "Unverified origin").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Sign").performClick()
        verify(callback).onSignClick()
    }

    @Test
    fun scamFlagAndBlockedSummaryEachDisableSigning() {
        val state = mutableStateOf(RequestPreviewViewState.default.copy(summary = summary, isBlocked = true))
        compose.setContent { FearlessAppTheme { RequestPreviewContent(state.value, callback) } }
        compose.onNodeWithText("Sign").assertIsNotEnabled().performClick()
        compose.runOnIdle { state.value = state.value.copy(isBlocked = false, summary = summary.copy(blocksSigning = true)) }
        compose.onNodeWithText("Sign").assertIsNotEnabled().performClick()
        verifyNoInteractions(callback)
    }
}
