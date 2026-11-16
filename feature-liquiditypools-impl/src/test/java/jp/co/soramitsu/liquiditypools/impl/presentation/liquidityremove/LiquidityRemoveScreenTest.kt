package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove

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
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel.Companion.ITEM_FEE_ID
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class LiquidityRemoveScreenTest {
    @get:Rule val compose = createComposeRule()
    private val callbacks = mockk<LiquidityRemoveCallbacks>(relaxed = true)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ready = LiquidityRemoveState(transferableAmount = "10 XOR", transferableFiat = "$20", buttonEnabled = true).copy(
        feeInfo = FeeInfoViewState(feeAmount = "0.01 XOR", feeAmountFiat = "$0.02")
    )

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    @Test fun readyStateShowsAmountsAndRoutesPrimaryAndInformationActions() {
        compose.setContent { FearlessAppTheme { LiquidityRemoveScreen(ready, callbacks) } }
        compose.onNodeWithText("10 XOR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0.01 XOR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_network_fee)).performScrollTo().performClick()
        verify { callbacks.onRemoveItemClick(ITEM_FEE_ID) }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(context.getString(R.string.common_preview)).performScrollTo().assertIsEnabled().performClick()
        verify(exactly = 1) { callbacks.onRemoveReviewClick() }
    }

    @Test fun unavailableStateExplainsWhyThePrimaryActionIsDisabled() {
        compose.setContent {
            FearlessAppTheme {
                LiquidityRemoveScreen(ready.copy(buttonEnabled = false, capabilityReason = "Runtime unavailable"), callbacks)
            }
        }
        compose.onNodeWithText("Runtime unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_preview)).performScrollTo().assertIsNotEnabled().performClick()
        verify(exactly = 0) { callbacks.onRemoveReviewClick() }
    }
}
