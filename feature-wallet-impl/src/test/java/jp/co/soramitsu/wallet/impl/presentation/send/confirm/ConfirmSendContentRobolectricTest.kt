package jp.co.soramitsu.wallet.impl.presentation.send.confirm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
class ConfirmSendContentRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : ConfirmSendScreenInterface {
        override fun copyRecipientAddressClicked() { calls += "copy" }
        override fun onNextClick() { calls += "next" }
        override fun onNavigationClick() { calls += "back" }
        override fun onItemClick(code: Int) { calls += "item:$code" }
    }

    @Before
    fun setUp() {
        ComposeFixtures.passDoubleTapGuard()
    }

    @Test
    fun nativeReviewShowsEveryValueAndConfirms() {
        render(state(nativeReview = true, chainIconUrl = "https://x/chain.svg"))

        compose.onNodeWithText(string(R.string.common_preview)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.sending)).assertIsDisplayed()
        compose.onNodeWithText("Polkadot").assertIsDisplayed()
        compose.onNodeWithText("Existential deposit").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("item:${ConfirmSendViewState.CODE_WARNING_CLICK}", "next")))
        assertEquals(5, state(nativeReview = true).tableItems.size)
    }

    @Test
    fun nativeReviewWithAMissingIdentityValueCannotConfirm() {
        render(state(nativeReview = true).copy(toInfoItem = TitleValueViewState("To", value = " ")))

        compose.onNodeWithText("Confirm").assertIsNotEnabled()
    }

    @Test
    fun feeLoadingKeepsTheButtonBusy() {
        render(state(nativeReview = false).copy(feeInfoItem = null, iconOverrideResId = R.drawable.ic_fearless_logo))

        assertEquals(0, compose.onAllNodes(hasText("Confirm")).fetchSemanticsNodes().size)
    }

    @Test
    fun loadingOverlayKeepsTheTableBehindIt() {
        render(state(nativeReview = false).copy(isLoading = true))

        assertTrue(compose.onAllNodes(hasText("Polkadot"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(fontScale = 1.5f)
    fun largeTextLiftsTheNetworkOutOfTheTable() {
        render(state(nativeReview = false))

        compose.onNodeWithText("Polkadot").assertIsDisplayed()
        compose.onNodeWithText("Network").assertIsDisplayed()
    }

    private fun render(state: ConfirmSendViewState) {
        compose.setContent {
            FearlessAppTheme {
                ConfirmSendContent(state = state, callback = callback)
            }
        }
        compose.waitForIdle()
    }

    private fun state(nativeReview: Boolean, chainIconUrl: String? = null) = ConfirmSendViewState(
        chainIconUrl = chainIconUrl,
        networkInfoItem = TitleValueViewState("Network", "Polkadot"),
        fromInfoItem = TitleValueViewState("From", "Main", additionalValue = "5Grwva"),
        toInfoItem = TitleValueViewState("To", "5FHneW"),
        amountInfoItem = TitleValueViewState("Amount", "1 DOT", additionalValue = "$6"),
        feeInfoItem = TitleValueViewState(
            "Existential deposit",
            "0.01 DOT",
            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, ConfirmSendViewState.CODE_WARNING_CLICK)
        ),
        buttonState = ButtonViewState("Confirm", true),
        nativeReview = nativeReview
    )
}
