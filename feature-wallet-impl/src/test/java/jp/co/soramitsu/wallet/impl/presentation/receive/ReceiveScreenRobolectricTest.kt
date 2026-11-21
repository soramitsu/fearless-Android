package jp.co.soramitsu.wallet.impl.presentation.receive

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.receive.model.ReceiveToggleType
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
class ReceiveScreenRobolectricTest(private val sunlit: Boolean) {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : ReceiveScreenInterface {
        override fun copyClicked() { calls += "copy" }
        override fun backClicked() { calls += "back" }
        override fun shareClicked() { calls += "share" }
        override fun tokenClicked() { calls += "token" }
        override fun receiveChanged(type: ReceiveToggleType) { calls += "toggle:$type" }
        override fun onAmountInput(amount: BigDecimal?) { calls += "amount:$amount" }
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
    fun readyQrOffersCopyShareAndRequestToggle() {
        render(state(qr = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), selection = ReceiveToggleType.Receive))

        compose.onNodeWithText(string(R.string.wallet_asset_receive_template, "DOT")).assertIsDisplayed()
        compose.onNodeWithText("DOT · Polkadot").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.ux_receive_instruction, "DOT", "Polkadot")).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.ux_receive_qr, "DOT", "Polkadot")).assertExists()
        compose.onNodeWithText("Main wallet").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_copy_address)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.ux_share_address)).performClick()
        compose.onNodeWithText(string(R.string.common_request)).performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("copy", "share", "toggle:Request")))
    }

    @Test
    fun requestModeShowsTheAmountInputAndWaitsForTheQr() {
        render(state(qr = null, selection = ReceiveToggleType.Request))

        compose.onNodeWithText("Balance: 10 DOT").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_copy_address)).assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.ux_share_address)).assertIsNotEnabled()
    }

    @Test
    fun loadingShowsOnlyTheProgress() {
        compose.setContent { ReceiveScreen(state = LoadingState.Loading(), callback = callback) }
        compose.waitForIdle()

        assertEquals(0, compose.onAllNodes(hasText(string(R.string.common_copy_address))).fetchSemanticsNodes().size)
    }

    private fun render(state: ReceiveScreenViewState) {
        compose.setContent { ReceiveScreen(state = LoadingState.Loaded(state), callback = callback) }
        compose.waitForIdle()
    }

    private fun state(qr: Bitmap?, selection: ReceiveToggleType) = ReceiveScreenViewState(
        qrCode = qr,
        assetSymbol = "DOT",
        account = WalletAccount("5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY", "Main wallet"),
        multiToggleButtonState = MultiToggleButtonState(selection, ReceiveToggleType.entries),
        amountInputViewState = AmountInputViewState(tokenName = "DOT", totalBalance = "Balance: 10 DOT", fiatAmount = "$6", tokenAmount = BigDecimal.ONE),
        requestAllowed = true,
        networkName = "Polkadot"
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "sunlit={0}")
        fun palettes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
