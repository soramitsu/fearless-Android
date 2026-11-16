package jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.component.NumberInputState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class TransactionSettingsContentTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val callbacks = mockk<TransactionSettingsCallbacks>(relaxed = true)
    private val state = TransactionSettingsViewState(
        marketState = SelectorState(title = "Market", subTitle = "TBC", iconUrl = null),
        slippageInputState = NumberInputState(title = "Slippage tolerance", value = "0.5", suffix = "%"),
        slippageWarningText = null
    )

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    private fun show(viewState: TransactionSettingsViewState) {
        compose.setContent { FearlessAppTheme { TransactionSettingsContent(viewState, callbacks) } }
    }

    @Test fun settingsShowTheMarketAndRouteEveryAction() {
        show(state)

        compose.onNodeWithText(context.getString(R.string.polkaswap_swap_settings_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.polkaswap_slippage_tolerance_description)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("TBC").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_close)).performClick()
        compose.onNodeWithText(context.getString(R.string.polkaswap_btn_reset_to_default)).performClick()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(context.getString(R.string.common_save)).performClick()

        verify(exactly = 1) {
            callbacks.onMarketClick()
            callbacks.onCloseClick()
            callbacks.onResetToDefaultClick()
            callbacks.onSaveClick()
        }
    }

    @Test fun typedToleranceSliderAndFocusReachTheCallbacks() {
        show(state)

        compose.onNode(hasSetTextAction()).performClick()
        verify { callbacks.onAmountFocusChanged(match { it.isFocused }) }
        compose.onNode(hasSetTextAction()).performTextReplacement("1.2")
        verify { callbacks.onSlippageValueChange("1.2") }

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.SetProgress) {
            it(2f)
        }
        // The slider snaps to its tick marks, so the reported value is the tick next to 2 %.
        verify { callbacks.onSlippageValueChange(match<Float> { abs(it - 2f) < 0.05f }) }
    }

    @Test fun warningIsShownOnlyWhenTheToleranceIsRisky() {
        show(state.copy(slippageWarningText = "Transaction may fail"))
        compose.onNodeWithText("Transaction may fail").assertIsDisplayed()
    }

    @Test fun safeToleranceShowsNoWarning() {
        show(state)
        compose.onNodeWithText("Transaction may fail").assertDoesNotExist()
    }

    @Test fun previewFixtureRendersTheDefaultSettings() {
        compose.setContent { FearlessAppTheme { TransactionSettingsContentPreview() } }

        compose.onNodeWithText("SMART").assertIsDisplayed()
        compose.onNodeWithText("Slippage Tolerance", substring = true).assertIsDisplayed()
    }

    @Suppress("DEPRECATION")
    @Test fun fragmentBundleCarriesTheInitialSettings() {
        val settings = TransactionSettingsModel(Market.TBC, 1.5)

        assertEquals(settings, TransactionSettingsFragment.getBundle(settings).get(TransactionSettingsFragment.SETTINGS_MODEL_KEY))
    }
}
