package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd

import android.content.Context
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
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class LiquidityAddFeeInfoTest {
    @get:Rule val compose = createComposeRule()
    private val callbacks = mockk<LiquidityAddCallbacks>(relaxed = true)
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    @Test
    fun networkFeeRowOpensTheFeeExplanation() {
        val state = LiquidityAddState(feeInfo = FeeInfoViewState(feeAmount = "0.02 XOR", feeAmountFiat = null))
        compose.setContent { FearlessAppTheme { LiquidityAddScreen(state, callbacks) } }

        compose.onNodeWithText(context.getString(R.string.common_network_fee)).performScrollTo().performClick()

        verify { callbacks.onAddTableItemClick(ITEM_FEE_ID) }
    }
}
