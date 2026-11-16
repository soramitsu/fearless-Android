package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel.Companion.ITEM_APY_ID
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class LiquidityAddScreenTest {
    @get:Rule val compose = createComposeRule()
    private val callbacks = mockk<LiquidityAddCallbacks>(relaxed = true)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ready = LiquidityAddState(apy = "12.5%", slippage = "0.7%", buttonEnabled = true).copy(
        feeInfo = FeeInfoViewState(feeAmount = "0.01 XOR", feeAmountFiat = "$0.02")
    )

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    @Test fun readyStateShowsAmountsAndRoutesPrimaryAndInformationActions() {
        compose.setContent { FearlessAppTheme { LiquidityAddScreen(ready, callbacks) } }
        compose.onNodeWithText("12.5%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0.01 XOR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.lp_apy_title)).performScrollTo().performClick()
        verify { callbacks.onAddTableItemClick(ITEM_APY_ID) }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Review").performScrollTo().assertIsEnabled().performClick()
        verify(exactly = 1) { callbacks.onAddReviewClick() }
    }

    @Test fun unavailableStateExplainsWhyThePrimaryActionIsDisabled() {
        compose.setContent {
            FearlessAppTheme {
                LiquidityAddScreen(ready.copy(buttonEnabled = false, capabilityReason = "Runtime unavailable"), callbacks)
            }
        }
        compose.onNodeWithText("Runtime unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Review").performScrollTo().assertIsNotEnabled().performClick()
        verify(exactly = 0) { callbacks.onAddReviewClick() }
    }
}
