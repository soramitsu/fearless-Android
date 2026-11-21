package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremoveconfirm

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
class LiquidityRemoveConfirmScreenTest {
    @get:Rule val compose = createComposeRule()
    private val callbacks = mockk<LiquidityRemoveConfirmCallbacks>(relaxed = true)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ready = LiquidityRemoveConfirmState(baseAmount = "10 XOR", targetAmount = "20 VAL", buttonEnabled = true).copy(
        feeInfo = FeeInfoViewState(feeAmount = "0.01 XOR", feeAmountFiat = "$0.02")
    )

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    @Test fun readyStateShowsAmountsAndRoutesPrimaryAndInformationActions() {
        compose.setContent { FearlessAppTheme { LiquidityRemoveConfirmScreen(ready, callbacks) } }
        compose.onNodeWithText("20 VAL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0.01 XOR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_network_fee)).performScrollTo().performClick()
        verify { callbacks.onRemoveConfirmItemClick(ITEM_FEE_ID) }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(context.getString(R.string.common_confirm)).performScrollTo().assertIsEnabled().performClick()
        verify(exactly = 1) { callbacks.onRemoveConfirmClick() }
    }

    @Test fun unavailableStateExplainsWhyThePrimaryActionIsDisabled() {
        compose.setContent {
            FearlessAppTheme {
                LiquidityRemoveConfirmScreen(ready.copy(buttonEnabled = false, capabilityReason = "Runtime unavailable"), callbacks)
            }
        }
        compose.onNodeWithText("Runtime unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_confirm)).performScrollTo().assertIsNotEnabled().performClick()
        verify(exactly = 0) { callbacks.onRemoveConfirmClick() }
    }
}
