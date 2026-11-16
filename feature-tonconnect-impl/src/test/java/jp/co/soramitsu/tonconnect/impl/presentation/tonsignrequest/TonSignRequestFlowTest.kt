package jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import jp.co.soramitsu.common.compose.component.InfoItemViewState
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w480dp-h1000dp")
class TonSignRequestFlowTest {
    @get:Rule val compose = createComposeRule()
    private val request = mock<TonSignRequestScreenInterface>()
    private val preview = mock<TonSignPreviewScreenInterface>()

    @Before
    fun advanceClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun requestTransitionsToDecodedPreviewAndDispatchesActions() {
        val state = mutableStateOf<TonSignRequestFlowState?>(
            TonSignRequestViewState.default.copy(
                connectionUrl = "https://ton.example",
                message = InfoItemViewState("Message", "Transfer request", singleLine = false),
                wallet = TonSignRequestViewState.default.wallet.copy(title = "TON wallet")
            )
        )
        compose.setContent { FearlessAppTheme { TonSignRequestFlow(state.value, request, preview) } }
        listOf("https://ton.example", "Transfer request", "TON wallet").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Preview").assertIsDisplayed().performClick()
        verify(request).onPreviewClick()
        compose.runOnIdle {
            state.value = TonSignPreviewViewState.default.copy(
                method = "sendTransaction",
                wallet = TonSignPreviewViewState.default.wallet.copy(title = "TON wallet"),
                tableItems = listOf(TitleValueViewState("Network", "TON mainnet")),
                summary = SigningSummaryViewState(
                    "Transfer", listOf(SigningSummarySection("Details", listOf(SigningDetailViewState("Amount", "2 TON")))),
                    emptyList(), false
                )
            )
        }
        listOf("sendTransaction", "TON mainnet", "2 TON").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Sign").performClick()
        verify(preview).onSignClick()
    }

    @Test
    fun undecodedPreviewCannotSignAndNullFlowRemovesContent() {
        val state = mutableStateOf<TonSignRequestFlowState?>(TonSignPreviewViewState.default)
        compose.setContent { FearlessAppTheme { TonSignRequestFlow(state.value, request, preview) } }
        compose.onNodeWithText("Sign").performClick()
        verifyNoInteractions(preview, request)
        compose.runOnIdle { state.value = null }
        compose.onNodeWithText("Sign").assertDoesNotExist()
        compose.onNodeWithText("Preview").assertDoesNotExist()
    }
}
