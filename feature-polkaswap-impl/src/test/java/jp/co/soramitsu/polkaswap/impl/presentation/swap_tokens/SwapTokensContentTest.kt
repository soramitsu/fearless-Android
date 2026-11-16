package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.math.BigDecimal
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class SwapTokensContentTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val callbacks = mockk<SwapTokensCallbacks>(relaxed = true)
    private val details = SwapDetailsViewState("xor", "val", "XOR", "VAL", null, null,
        "2 XOR", "7.5 VAL", "7 VAL", "$14", "3.75", "0.26", "Minimum received", "XOR → VAL")
    private val base = SwapTokensContentViewState(
        AmountInputViewState.defaultObj.copy(tokenName = "XOR", title = "From", allowAssetChoose = true),
        AmountInputViewState.defaultObj.copy(tokenName = "VAL", title = "To", allowAssetChoose = true),
        Market.SMART, null, false, LoadingState.Loaded(null), true, true, false
    )
    private val fee = SwapDetailsViewState.NetworkFee("XOR", "0.01 XOR", "$0.02")

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    @Test fun incompleteSwapDisablesPreviewAndExposesSettingsDirectionAndTokenActions() {
        compose.setContent { FearlessAppTheme { SwapTokensContent(base, callbacks, Modifier.fillMaxSize()) } }
        compose.onNodeWithText(context.getString(R.string.common_preview)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_swap_settings)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_swap_direction)).performClick()
        compose.onNodeWithText("XOR").performClick()
        compose.onNodeWithText("VAL").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_back)).performClick()
        verify {
            callbacks.onMarketSettingsClick(); callbacks.onChangeTokensClick(); callbacks.onFromTokenSelect()
            callbacks.onToTokenSelect(); callbacks.onBackClick()
        }
    }

    @Test fun readyQuoteShowsMinimumFeeAndExpandableRouteAndRates() {
        compose.setContent { FearlessAppTheme { SwapTokensContent(base.copy(swapDetailsViewState = details,
            networkFeeViewState = LoadingState.Loaded(fee)), callbacks, Modifier.fillMaxSize()) } }
        compose.onNodeWithText("7 VAL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0.01 XOR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.ux_show_details)).performScrollTo().performClick()
        compose.onNodeWithText("XOR → VAL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3.75").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("XOR / VAL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("VAL / XOR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0.26").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(Market.SMART.marketName).performScrollTo().assertIsDisplayed()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(context.getString(R.string.ux_hide_details)).performScrollTo().performClick()
        compose.onNodeWithText("XOR → VAL").assertDoesNotExist()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(context.getString(R.string.common_preview)).assertIsEnabled().performClick()
        verify(exactly = 1) { callbacks.onPreviewClick() }
    }

    @Test fun quoteTooltipsReachTheirCallbacks() {
        compose.setContent { FearlessAppTheme { SwapTokensContent(base.copy(swapDetailsViewState = details,
            networkFeeViewState = LoadingState.Loaded(fee)), callbacks, Modifier.fillMaxSize()) } }
        compose.onNodeWithText("Minimum received").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.common_network_fee)).performScrollTo().performClick()
        verify(exactly = 1) { callbacks.minMaxToolTopClick(); callbacks.networkFeeTooltipClick() }
    }

    @Test fun disclaimerNoticeOffersReviewBeforeAcceptance() {
        compose.setContent { FearlessAppTheme { SwapTokensContent(base.copy(hasReadDisclaimer = false), callbacks, Modifier.fillMaxSize()) } }
        compose.onNodeWithText(context.getString(R.string.polkaswap_disclaimer_message)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_read)).performClick()
        verify { callbacks.onDisclaimerClick() }
    }

    @Test fun acceptedDisclaimerHidesTheNotice() {
        compose.setContent { FearlessAppTheme { SwapTokensContent(base, callbacks, Modifier.fillMaxSize()) } }
        compose.onNodeWithText(context.getString(R.string.polkaswap_disclaimer_message)).assertDoesNotExist()
    }

    @Test fun typedAmountsReachTheCorrectCallbacks() {
        compose.setContent { FearlessAppTheme { SwapTokensContent(base, callbacks, Modifier.fillMaxSize()) } }
        val inputs = compose.onAllNodes(hasSetTextAction())
        inputs[0].performTextReplacement("2.5")
        inputs[1].performTextReplacement("4")
        verify { callbacks.onFromAmountChange(BigDecimal("2.5")); callbacks.onToAmountChange(BigDecimal("4")) }
    }

    @Test fun quickAmountsAppearOnlyForAFocusedSourceWithOpenKeyboard() {
        val focusedFrom = base.fromAmountInputViewState.copy(isFocused = true)
        val state = mutableStateOf(base.copy(fromAmountInputViewState = focusedFrom))
        compose.setContent { FearlessAppTheme { SwapTokensContent(state.value, callbacks, Modifier.fillMaxSize()) } }
        compose.onNodeWithText("50%").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(isSoftKeyboardOpen = true) }
        compose.onNodeWithText("50%").performClick()
        verify(exactly = 1) { callbacks.onQuickAmountInput(0.5) }
        compose.runOnIdle {
            state.value = state.value.copy(fromAmountInputViewState = focusedFrom.copy(tokenName = null))
        }
        compose.onNodeWithText("50%").assertDoesNotExist()
    }

    @Test fun feeLoadingKeepsTheQuoteAndTheLoadingOverlayHidesTheForm() {
        // The fee shimmer and the progress overlay animate forever: drive frames by hand.
        compose.mainClock.autoAdvance = false
        val state = mutableStateOf(base.copy(swapDetailsViewState = details, networkFeeViewState = LoadingState.Loading()))
        compose.setContent { FearlessAppTheme { SwapTokensContent(state.value, callbacks, Modifier.fillMaxSize()) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Minimum received").assertExists()
        compose.onNodeWithText("7 VAL").assertExists()
        compose.onNodeWithText(context.getString(R.string.common_network_fee)).assertExists()
        compose.onNodeWithText("0.01 XOR").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(isLoading = true) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(context.getString(R.string.common_preview)).assertDoesNotExist()
        compose.onNodeWithText("Minimum received").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_swap_settings)).assertExists()
        verify(exactly = 0) { callbacks.onPreviewClick() }
    }

    @Test fun fragmentBundleNamesTheNetworkAndBothAssets() {
        val bundle = SwapTokensFragment.getBundle("sora", "xor", null)

        assertEquals("sora", bundle.getString(SwapTokensFragment.KEY_SELECTED_CHAIN_ID))
        assertEquals("xor", bundle.getString(SwapTokensFragment.KEY_SELECTED_ASSET_FROM_ID))
        assertTrue(bundle.containsKey(SwapTokensFragment.KEY_SELECTED_ASSET_TO_ID))
        assertNull(bundle.getString(SwapTokensFragment.KEY_SELECTED_ASSET_TO_ID))
    }

    @Test fun previewFixtureRendersBothAmountFields() {
        compose.setContent { SwapTokensContentPreview() }

        compose.onNodeWithText("From title").assertIsDisplayed()
        compose.onNodeWithText("To title").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.polkaswap_disclaimer_message)).assertIsDisplayed()
    }
}
