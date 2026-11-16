package jp.co.soramitsu.wallet.impl.presentation.send.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AddressInputWithScore
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h2000dp")
class SendSetupContentRobolectricTest(private val sunlit: Boolean) {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : SendSetupScreenInterface {
        override val onCrossChainClick: () -> Unit = { calls += "crossChain" }
        override fun onNavigationClick() { calls += "back" }
        override fun onAddressInput(input: String) { calls += "address:$input" }
        override fun onAddressInputClear() { calls += "clear" }
        override fun onAmountInput(input: BigDecimal?) { calls += "amount:$input" }
        override fun onChainClick() { calls += "chain" }
        override fun onTokenClick() { calls += "token" }
        override fun onNextClick() { calls += "next" }
        override fun onQrClick() { calls += "qr" }
        override fun onHistoryClick() { calls += "history" }
        override fun onPasteClick() { calls += "paste" }
        override fun onAmountFocusChanged(isFocused: Boolean) { calls += "focus:$isFocused" }
        override fun onQuickAmountInput(input: Double) { calls += "quick:$input" }
        override fun onWarningInfoClick() { calls += "warning" }
        override fun onSendAllChecked(checked: Boolean) { calls += "sendAll:$checked" }
        override fun onCommentInput(value: String) { calls += "comment:$value" }
    }

    @Before
    fun setUp() {
        ComposeFixtures.passDoubleTapGuard()
        if (!sunlit) {
            mockkObject(FwRestyle)
            every { FwRestyle.isEnabled(any()) } returns false
        }
    }

    @After
    fun tearDown() {
        unmockkObject(FwRestyle)
    }

    @Test
    fun editableSendOffersEveryInputAndAction() {
        render(state())

        compose.onNodeWithText(string(R.string.send_fund)).assertIsDisplayed()
        compose.onNodeWithText("Paste a Polkadot address").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.ux_send_maximum_description)).assertIsDisplayed()
        compose.onNodeWithText("Polkadot").performClick()
        compose.onNodeWithText("Fee is high").performClick()
        compose.onNodeWithText(string(R.string.ux_network_transfer)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.ux_send_maximum)).performClick()
        compose.onNodeWithText(string(R.string.chip_qr), ignoreCase = true).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.chip_history), ignoreCase = true).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("chain", "warning", "crossChain", "sendAll:true", "qr", "history", "next")))
    }

    @Test
    fun nativeReviewShowsTheFeeInline() {
        render(state(nativeReview = true, fee = FeeInfoViewState(caption = null, feeAmount = "0.01 DOT", feeAmountFiat = "$0.06")))

        compose.onNodeWithText(string(R.string.common_network_fee)).assertIsDisplayed()
        compose.onNodeWithText("0.01 DOT").assertIsDisplayed()
        compose.onNodeWithText("$0.06").assertIsDisplayed()
    }

    @Test
    fun lockedInputHidesTheChipsAndCrossChain() {
        render(state(locked = true, sendAll = false, address = AddressInputWithScore.Filled("To", "5Grwva", "", 80)))

        assertEquals(0, compose.onAllNodes(hasText(string(R.string.chip_qr))).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText(string(R.string.ux_network_transfer))).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText(string(R.string.ux_send_maximum))).fetchSemanticsNodes().size)
    }

    @Test
    fun focusedAmountWithKeyboardOffersQuickInputs() {
        render(state(focused = true))

        compose.onNodeWithText("25%").performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.contains("quick:0.25"))
    }

    @Test
    @Config(fontScale = 1.5f)
    fun largeTextStacksTheNetworkAndFee() {
        render(state(fee = FeeInfoViewState(caption = "Network fee", feeAmount = "0.02 DOT", feeAmountFiat = null)))

        compose.onNodeWithText("0.02 DOT").assertIsDisplayed()
        compose.onNodeWithText("Polkadot").performClick()
        compose.waitForIdle()

        assertTrue(calls.contains("chain"))
    }

    private fun render(state: SendSetupViewState) {
        compose.setContent {
            FearlessAppTheme {
                SendSetupContent(state = state, callback = callback)
            }
        }
        compose.waitForIdle()
    }

    private fun state(
        nativeReview: Boolean = false,
        locked: Boolean = false,
        sendAll: Boolean = true,
        focused: Boolean = false,
        fee: FeeInfoViewState = FeeInfoViewState("Network fee", "0.01 DOT", "$0.06"),
        address: AddressInputWithScore = AddressInputWithScore.Empty("Send to", "Paste a Polkadot address")
    ) = SendSetupViewState(
        toolbarState = ToolbarViewState("Send", R.drawable.ic_arrow_left_24),
        addressInputState = address,
        amountInputState = AmountInputViewState(
            tokenName = "DOT",
            tokenImage = "",
            totalBalance = "Balance: 10 DOT",
            fiatAmount = "$60",
            tokenAmount = BigDecimal("1"),
            title = "Amount",
            isFocused = focused,
            allowAssetChoose = true
        ),
        chainSelectorState = SelectorState("Network", "Polkadot", null),
        feeInfoState = fee,
        warningInfoState = WarningInfoState("Fee is high", emptyList()),
        buttonState = ButtonViewState("Continue", true),
        isSoftKeyboardOpen = focused,
        isInputLocked = locked,
        isHistoryAvailable = true,
        sendAllChecked = false,
        sendAllAllowed = sendAll,
        commentState = TextInputViewState("", "Comment"),
        recipientHint = "Send to your own account",
        nativeReview = nativeReview,
        crossChainAvailable = true
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "sunlit={0}")
        fun palettes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
