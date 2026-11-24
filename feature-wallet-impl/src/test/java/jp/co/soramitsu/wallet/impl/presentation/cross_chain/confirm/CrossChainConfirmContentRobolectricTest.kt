package jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h2000dp")
class CrossChainConfirmContentRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : CrossChainConfirmScreenInterface {
        override fun copyRecipientAddressClicked() { calls += "copy" }
        override fun onNextClick() { calls += "next" }
        override fun onSendAgainClick() { calls += "again" }
        override fun onNavigationClick() { calls += "back" }
        override fun onItemClick(code: Int) { calls += "item:$code" }
    }

    @Before
    fun setUp() {
        ComposeFixtures.passDoubleTapGuard()
    }

    @Test
    fun alreadySentTransferShowsThePendingHashAndASendAgainChoice() {
        render(
            state().copy(
                originChainIcon = GradientIconData("https://x/p.svg", "E6007A"),
                destinationChainIcon = GradientIconData(null, null),
                pendingMessage = "An earlier transfer is still settling",
                pendingTransactionHash = "0xabc",
                sendAgainVisible = true
            )
        )

        compose.onNodeWithText(string(R.string.common_preview)).assertIsDisplayed()
        compose.onNodeWithText("2 DOT").assertIsDisplayed()
        compose.onNodeWithText("An earlier transfer is still settling").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.pool_join_recovery_hash) + ": 0xabc").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasAnySibling(hasText("Origin fee")), useUnmergedTree = true).performClick()
        compose.onNodeWithText(string(R.string.cross_chain_send_again)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("item:${CrossChainConfirmViewState.CODE_WARNING_CLICK}", "again", "next")))
    }

    @Test
    fun plainConfirmationHasNoSendAgain() {
        render(state().copy(isLoading = true))

        assertEquals(0, compose.onAllNodes(hasText(string(R.string.cross_chain_send_again)), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(5, state().tableItems.size)
        assertEquals("", CrossChainConfirmViewState.default.buttonState.text)
    }

    private fun render(state: CrossChainConfirmViewState) {
        compose.setContent {
            FearlessAppTheme {
                CrossChainConfirmContent(state = state, callback = callback)
            }
        }
        compose.waitForIdle()
    }

    private fun state() = CrossChainConfirmViewState(
        originChainIcon = null,
        destinationChainIcon = null,
        fromInfoItem = TitleValueViewState("From", "Main"),
        toInfoItem = TitleValueViewState("To", "5FHneW"),
        originNetworkItem = TitleValueViewState("Origin", "Polkadot"),
        destinationNetworkItem = TitleValueViewState("Destination", "Asset Hub"),
        amountInfoItem = TitleValueViewState("Amount", "2 DOT"),
        originFeeInfoItem = TitleValueViewState(
            "Origin fee",
            "0.01 DOT",
            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, CrossChainConfirmViewState.CODE_WARNING_CLICK)
        ),
        buttonState = ButtonViewState("Confirm", true)
    )
}
