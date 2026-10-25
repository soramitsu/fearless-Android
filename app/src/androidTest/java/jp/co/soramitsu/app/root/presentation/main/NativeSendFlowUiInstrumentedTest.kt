package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.compose.component.*
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.wallet.impl.presentation.send.confirm.*
import jp.co.soramitsu.wallet.impl.presentation.send.setup.*
import java.io.File
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the production bottom-sheet theme and public fixed reviews, without any signing callback. */
@RunWith(AndroidJUnit4::class)
class NativeSendFlowUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    @Test fun bitcoinConfirmationShowsExactAmountFeeRateNetworkAndReachableSend() = confirm(false)
    @Test fun solanaConfirmationSeparatesNetworkFeeAndAccountRent() = confirm(true)

    private fun confirm(solana: Boolean) {
        val network = if (solana) "Solana Devnet" else "Bitcoin Testnet"
        val amount = if (solana) "1.250001 USDC" else "0.00439718 BTC"
        val fee = if (solana) "0.000005005 SOL" else "0.00000282 BTC"
        val recipient = if (solana) "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk" else "tb1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        var sends = 0; var warnings = 0
        val callback = object : ConfirmSendScreenInterface {
            override fun copyRecipientAddressClicked() {}
            override fun onNextClick() { sends++ }
            override fun onNavigationClick() {}
            override fun onItemClick(code: Int) { warnings++ }
        }
        compose.setContent { FearlessTheme { ConfirmSendContent(ConfirmSendViewState("",
            networkInfoItem = TitleValueViewState("Network", network),
            fromInfoItem = TitleValueViewState("From", "Public test wallet"),
            toInfoItem = TitleValueViewState("To", recipient, clickState = TitleValueViewState.ClickState.Value(jp.co.soramitsu.common.R.drawable.ic_info_14, 3)),
            amountInfoItem = TitleValueViewState("Amount", amount),
            feeInfoItem = TitleValueViewState("Network fee", fee, "$0.01"),
            accountCreationInfoItem = if (solana) TitleValueViewState("Account creation", "0.00203928 SOL", "$0.31") else null,
            feeRateInfoItem = if (!solana) TitleValueViewState("Fee rate", "2 sat/vB") else null,
            buttonState = ButtonViewState("Send", true), nativeReview = true), callback) } }
        val kind = if (solana) "solana" else "bitcoin"
        compose.onNodeWithText(network).performScrollTo().assertIsDisplayed(); save("$kind-confirm-network")
        compose.onAllNodesWithText(amount, useUnmergedTree = true).onLast().performScrollTo(); save("$kind-confirm-amount")
        unclipped(compose.onAllNodesWithText(amount, useUnmergedTree = true).onLast())
        compose.onNodeWithText(fee, useUnmergedTree = true).performScrollTo(); save("$kind-confirm-fee"); unclipped(compose.onNodeWithText(fee, useUnmergedTree = true))
        if (solana) { compose.onNodeWithText("0.00203928 SOL").performScrollTo(); unclipped(compose.onNodeWithText("0.00203928 SOL")) }
        else { compose.onNodeWithText("2 sat/vB").performScrollTo(); unclipped(compose.onNodeWithText("2 sat/vB")) }
        compose.onNodeWithText(recipient, useUnmergedTree = true).performScrollTo(); unclipped(compose.onNodeWithText(recipient, useUnmergedTree = true)); compose.onNodeWithText(recipient, useUnmergedTree = true).performClick()
        compose.onNodeWithText("Send").performScrollTo().assertIsEnabled().performClick(); save("$kind-confirm-send")
        compose.runOnIdle { assertEquals(1, sends); assertEquals(1, warnings) }
    }

    @Test fun bitcoinSetupKeepsExactNetworkRecipientGuidanceAmountAndMaxReachable() {
        var next = 0; var maximum = 0; var entered: BigDecimal? = null
        val base = SendSetupViewState(ToolbarViewState("Send", jp.co.soramitsu.common.R.drawable.ic_arrow_left_24),
            AddressInputWithScore.Filled("Send to", "tb1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", "", 100),
            AmountInputViewState("BTC", "", "0.0044 BTC", "$422.13", BigDecimal("0.00439718"), "Amount", isFocused = true),
            SelectorState("Network", "Bitcoin Testnet", null), FeeInfoViewState(caption = "Transfer fee", feeAmount = "0.00000282 BTC", feeAmountFiat = "$0.27"),
            null, ButtonViewState("Continue", true), true, false,
            quickAmountInputValues = listOf(object : QuickInput { override val label = "Max"; override val value = 1.0 }), isHistoryAvailable = false, sendAllChecked = false,
            sendAllAllowed = false, commentState = null, recipientHint = "Use a Bitcoin SegWit address starting with bc1q or tb1q on the selected network.", nativeReview = true)
        val callback = object : SendSetupScreenInterface {
            override val onCrossChainClick: () -> Unit = {}
            override fun onNavigationClick() {}
            override fun onAddressInput(input: String) {}
            override fun onAddressInputClear() {}
            override fun onAmountInput(input: BigDecimal?) { entered = input }
            override fun onChainClick() {}
            override fun onTokenClick() {}
            override fun onNextClick() { next++ }
            override fun onQrClick() {}
            override fun onHistoryClick() {}
            override fun onPasteClick() {}
            override fun onAmountFocusChanged(isFocused: Boolean) {}
            override fun onQuickAmountInput(input: Double) { maximum++ }
            override fun onWarningInfoClick() {}
            override fun onSendAllChecked(checked: Boolean) {}
            override fun onCommentInput(value: String) {}
        }
        compose.setContent { FearlessTheme { SendSetupContent(base, callback) } }
        compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.common.R.string.ux_network_transfer)).assertDoesNotExist()
        compose.onNodeWithText(base.recipientHint!!).performScrollTo().assertIsDisplayed(); save("bitcoin-setup-recipient")
        compose.onNodeWithTag("InputAmountFieldBTC").performScrollTo().assertIsDisplayed(); save("bitcoin-setup-amount")
        unclipped(compose.onNodeWithTag("InputAmountFieldBTC"))
        compose.onNodeWithTag("InputAmountFieldBTC").performTextReplacement("0.00439719")
        compose.runOnIdle { assertEquals(0, requireNotNull(entered).compareTo(BigDecimal("0.00439719"))) }
        compose.onNodeWithText("Bitcoin Testnet", useUnmergedTree = true).performScrollTo().assertIsDisplayed(); save("bitcoin-setup-network")
        unclipped(compose.onNodeWithText("Bitcoin Testnet", useUnmergedTree = true))
        compose.onNodeWithText("0.00000282 BTC").performScrollTo().assertIsDisplayed(); save("bitcoin-setup-fee"); unclipped(compose.onNodeWithText("0.00000282 BTC", useUnmergedTree = true))
        compose.onNodeWithText("Max").assertIsDisplayed().performClick()
        compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, next); assertEquals(1, maximum) }
    }

    @Test fun nativeConfirmationWaitsForCompleteIdentityEvenWhenFeeIsReady() {
        var review by mutableStateOf(ConfirmSendViewState("", feeInfoItem = TitleValueViewState("Fee", "0.000005005 SOL"),
            nativeReview = true, buttonState = ButtonViewState("Send", true)))
        val callback = object : ConfirmSendScreenInterface {
            override fun copyRecipientAddressClicked() {}
            override fun onNextClick() { fail("Incomplete review authorized Send") }
            override fun onNavigationClick() {}
            override fun onItemClick(code: Int) {}
        }
        compose.setContent { FearlessTheme { ConfirmSendContent(review, callback) } }
        compose.onNodeWithText("Send").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { review = review.copy(networkInfoItem = TitleValueViewState("Network", "Solana Devnet"),
            fromInfoItem = TitleValueViewState("From", "Public test wallet"), toInfoItem = TitleValueViewState("To", "Recipient"),
            amountInfoItem = TitleValueViewState("Amount", "1 SOL")) }
        compose.onNodeWithText("Send").performScrollTo().assertIsEnabled()
    }

    private fun unclipped(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Expected a rendered text layout", layouts.isNotEmpty())
        assertTrue("A reviewed payment value is clipped: " + layouts.joinToString { "text=${it.layoutInput.text} size=${it.size} overflowW=${it.didOverflowWidth} overflowH=${it.didOverflowHeight} lines=${it.lineCount} constraints=${it.layoutInput.constraints}" }, layouts.none { it.hasVisualOverflow })
    }
    private fun save(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(200)
        val bitmap = (0 until 5).firstNotNullOfOrNull {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also { image ->
                if (image == null) android.os.SystemClock.sleep(200)
            }
        } ?: error("Screenshot unavailable after five attempts")
        val dir = compose.activity.getExternalFilesDir("wp9-flow-evidence")!!.apply { mkdirs() }
        File(dir, "$name-${(compose.activity.resources.configuration.fontScale * 100).toInt()}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
