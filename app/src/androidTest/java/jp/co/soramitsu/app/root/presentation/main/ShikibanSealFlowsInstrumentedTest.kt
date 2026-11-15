package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.ConfirmScreen
import jp.co.soramitsu.common.compose.component.ConfirmScreenViewState
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.common.compose.theme.fw.LocalFwSecureSurface
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.polkaswap.impl.presentation.swap_preview.SwapPreviewCallbacks
import jp.co.soramitsu.polkaswap.impl.presentation.swap_preview.SwapPreviewContent
import jp.co.soramitsu.polkaswap.impl.presentation.swap_preview.SwapPreviewState
import jp.co.soramitsu.success.presentation.SuccessContent
import jp.co.soramitsu.success.presentation.SuccessScreenInterface
import jp.co.soramitsu.success.presentation.SuccessViewState
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm.CrossChainConfirmContent
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm.CrossChainConfirmScreenInterface
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm.CrossChainConfirmViewState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The seal (brand kit SPEC v4 part 3) on the real confirmation screens of the cross-chain transfer, the Polkaswap swap
 * and the shared staking confirmation (pool operations, validator selection), and its continuation on the success
 * screen: waiting, accepted, broken. Public placeholder values only; nothing is signed or sent. Captures are saved for
 * frame review. The cross-chain and swap confirmations are secure surfaces (only press feedback moves), so they are
 * drawn with LocalFwSecureSurface as their fragments provide it.
 */
@RunWith(AndroidJUnit4::class)
class ShikibanSealFlowsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    private val dir by lazy { File(compose.activity.getExternalFilesDir(null), "seal-flows").apply { mkdirs() } }

    @After fun clear() = TransferSeal.clear()

    private fun save(name: String): Bitmap {
        val bitmap = compose.onNodeWithTag(ROOT).captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return bitmap
    }

    private fun count(bitmap: Bitmap, predicate: (Int, Int, Int) -> Boolean): Int {
        var n = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val p = bitmap.getPixel(x, y)
            if (predicate((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)) n++
        }
        return n
    }

    private val gold = { r: Int, g: Int, b: Int -> r > 220 && g > 170 && b in 60..170 }
    // Ember #FF5A4D (green and blue both well above zero and close together), not the brand pinks (green near zero).
    private val ember = { r: Int, g: Int, b: Int -> r > 150 && g in 35..150 && b in 25..140 && kotlin.math.abs(g - b) < 50 && g < r / 2 + 30 }

    private fun host(secure: Boolean, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FearlessTheme {
                CompositionLocalProvider(
                    LocalFwEffects provides FwEffects(FwTier.Full, highContrast = false, reduceMotion = false),
                    LocalFwSecureSurface provides secure
                ) {
                    Box(Modifier.fillMaxSize().background(FwStandard.bgBase).testTag(ROOT)) { content() }
                }
            }
        }
    }

    private fun waiting() = TransferSeal.set(TransferSeal.Record(readyAtMs = TransferSeal.nowMs() - 2_000))
    private fun failedNow() = TransferSeal.set(TransferSeal.Record(readyAtMs = TransferSeal.nowMs() - 2_000, failedAtMs = TransferSeal.nowMs()))

    // --- Cross-chain transfer -----------------------------------------------------------------------------------------

    private val crossChainCallbacks = object : CrossChainConfirmScreenInterface {
        override fun copyRecipientAddressClicked() = Unit
        override fun onNextClick() = Unit
        override fun onSendAgainClick() = Unit
        override fun onNavigationClick() = Unit
        override fun onItemClick(code: Int) = Unit
    }

    @Test fun crossChainWaitingShowsTheStageReachedStill_andAFailureShowsTheBrokenSealWithoutMovingTheScreen() {
        waiting()
        val loading = androidx.compose.runtime.mutableStateOf(true)
        host(secure = true) {
            CrossChainConfirmContent(CrossChainConfirmViewState.default.copy(isLoading = loading.value, buttonState = ButtonViewState("Confirm", true)), crossChainCallbacks)
        }
        compose.mainClock.advanceTimeBy(200)
        val wait = save("cross-chain-1-waiting")
        assertTrue("the rings of the stage reached are drawn", count(wait, gold) > 50)
        compose.mainClock.advanceTimeBy(600)
        val still = save("cross-chain-1-waiting-later")
        assertTrue("a secure confirmation's seal does not move", wait.sameAs(still))
        // The screen without any seal, then the same screen as the failure arrives: nothing may move.
        TransferSeal.clear()
        loading.value = false
        compose.mainClock.advanceTimeBy(32)
        val before = compose.onNodeWithText("Confirm").fetchSemanticsNode().boundsInRoot
        val baseline = count(save("cross-chain-0-without-seal"), ember)
        failedNow()
        compose.mainClock.advanceTimeBy(32)
        val broken = save("cross-chain-3-failed")
        assertTrue("a definite failure shows the ember seal", count(broken, ember) > baseline + 20)
        assertBoundsEqual(before, compose.onNodeWithText("Confirm").fetchSemanticsNode().boundsInRoot)
    }

    // --- Polkaswap swap -----------------------------------------------------------------------------------------------

    private fun swapState(loading: Boolean) = SwapPreviewState(
        swapDetailsViewState = SwapDetailsViewState(
            fromTokenId = "from", toTokenId = "to", fromTokenName = "XOR", toTokenName = "VAL", fromTokenImage = GradientIconState.Local(jp.co.soramitsu.common.R.drawable.ic_fearless_logo),
            toTokenImage = GradientIconState.Local(jp.co.soramitsu.common.R.drawable.ic_fearless_logo), fromTokenAmount = "1", toTokenAmount = "2", toTokenMinReceived = "1.9", toFiatMinReceived = "$1",
            fromTokenOnToToken = "2", toTokenOnFromToken = "0.5", minmaxTitle = "Minimum received", route = "XOR → VAL"
        ),
        networkFee = SwapDetailsViewState.NetworkFee("XOR", "0.0007", null),
        isLoading = loading
    )

    @Test fun swapWaitingShowsTheStageReachedStill_andAFailureShowsTheBrokenSeal() {
        waiting()
        val loading = androidx.compose.runtime.mutableStateOf(true)
        host(secure = true) {
            SwapPreviewContent(swapState(loading.value), object : SwapPreviewCallbacks {
                override fun onBackClick() = Unit
                override fun onConfirmClick() = Unit
            })
        }
        compose.mainClock.advanceTimeBy(200)
        val wait = save("swap-1-waiting")
        assertTrue(count(wait, gold) > 50)
        TransferSeal.clear()
        loading.value = false
        compose.mainClock.advanceTimeBy(32)
        val baseline = count(save("swap-0-without-seal"), ember)
        failedNow()
        compose.mainClock.advanceTimeBy(32)
        assertTrue(count(save("swap-3-failed"), ember) > baseline + 20)
    }

    // --- Staking (shared confirmation: pool operations and validator selection) ---------------------------------------

    private fun stakingState(loading: Boolean) = ConfirmScreenViewState(
        toolbarViewState = ToolbarViewState("Confirm", null),
        amount = "10 DOT",
        tableItems = emptyList(),
        assetIcon = GradientIconState.Local(jp.co.soramitsu.common.R.drawable.ic_fearless_logo),
        titleRes = jp.co.soramitsu.common.R.string.common_confirm,
        isLoading = loading
    )

    @Test fun stakingWaitingDrawsTheSeal_aFailureBreaksIt_andNothingMovesWhenTheResultArrives() {
        waiting()
        val loading = androidx.compose.runtime.mutableStateOf(true)
        host(secure = false) { ConfirmScreen(stakingState(loading.value), onNavigationClick = {}, onConfirm = {}) }
        compose.mainClock.advanceTimeBy(400)
        val wait = save("staking-1-waiting")
        assertTrue(count(wait, gold) > 50)
        // The screen without any seal, then the same screen as the failure arrives: nothing may move.
        TransferSeal.clear()
        loading.value = false
        compose.mainClock.advanceTimeBy(32)
        val before = compose.onNodeWithText("10 DOT").fetchSemanticsNode().boundsInRoot
        val baseline = count(save("staking-0-without-seal"), ember)
        failedNow()
        compose.mainClock.advanceTimeBy(32)
        val broken = save("staking-3-failed")
        assertTrue(count(broken, ember) > baseline + 20)
        assertBoundsEqual(before, compose.onNodeWithText("10 DOT").fetchSemanticsNode().boundsInRoot)
        // After the break the overlay goes: the screen is exactly as without the seal.
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(baseline, count(save("staking-4-after-break"), ember))
    }

    // --- The success screen continues the same figure ----------------------------------------------------------------

    @Test fun theSuccessScreenContinuesTheAcceptedSeal() {
        val now = TransferSeal.nowMs()
        val record = TransferSeal.Record(readyAtMs = now - 1_000, signedAtMs = now - 100, sentAtMs = now - 100, acceptedAtMs = now - 100)
        host(secure = false) {
            SuccessContent(
                state = SuccessViewState("Transaction sent", "Your transaction has been successfully sent to the blockchain", emptyList(), null),
                callback = object : SuccessScreenInterface {
                    override fun onClose() = Unit
                    override fun onItemClick(code: Int) = Unit
                    override fun onExplorerClick() = Unit
                    override fun onShareClick() = Unit
                },
                seal = record
            )
        }
        compose.mainClock.advanceTimeBy(100)
        val accepted = save("success-2-accepted")
        assertTrue("the triangle and rings of the accepted seal are drawn", count(accepted, gold) > 100)
        compose.onNodeWithTag("success_solar_ignite").assertExists()
    }

    private fun assertBoundsEqual(expected: Rect, actual: Rect) {
        assertEquals("layout moved when the result arrived", expected, actual)
    }

    private companion object {
        const val ROOT = "seal-flow-root"
    }
}
