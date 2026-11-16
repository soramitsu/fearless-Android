package jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel.Companion.ITEM_APY_ID
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class PoolDetailsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val callbacks = mockk<PoolDetailsCallbacks>(relaxed = true)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val base = mockk<Asset> { every { symbol } returns "xor"; every { iconUrl } returns "" }
    private val target = mockk<Asset> { every { symbol } returns "val"; every { iconUrl } returns "" }

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    @Test fun fundedPoolShowsBothBalancesAndSupportsSupplyRemovalAndYieldInfo() {
        val state = PoolDetailsState(base, target, "2 XOR", "$4", "5 VAL", "$10", "$14", "12%")
        compose.setContent { FearlessAppTheme { PoolDetailsScreen(state, callbacks) } }
        compose.onNodeWithText("2 XOR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("5 VAL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.lp_apy_title)).performScrollTo().performClick()
        verify { callbacks.onDetailItemClick(ITEM_APY_ID) }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(context.getString(R.string.lp_supply_button_title)).performScrollTo().performClick()
        verify { callbacks.onSupplyLiquidityClick() }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(context.getString(R.string.lp_remove_button_title)).performScrollTo().performClick()
        verify { callbacks.onRemoveLiquidityClick() }
    }

    @Test fun poolWithoutOwnedLiquidityOffersSupplyButNoRemoval() {
        compose.setContent { FearlessAppTheme { PoolDetailsScreen(PoolDetailsState(base, target, tvl = "$100"), callbacks) } }
        compose.onNodeWithText("$100").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.lp_remove_button_title)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.lp_supply_button_title)).performScrollTo().performClick()
        verify(exactly = 1) { callbacks.onSupplyLiquidityClick() }
        verify(exactly = 0) { callbacks.onRemoveLiquidityClick() }
    }
}
