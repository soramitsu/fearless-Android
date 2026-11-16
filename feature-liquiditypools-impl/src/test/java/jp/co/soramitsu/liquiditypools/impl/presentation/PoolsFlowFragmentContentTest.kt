package jp.co.soramitsu.liquiditypools.impl.presentation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.AllPoolsState
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.BasicPoolListItemState
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd.LiquidityAddState
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm.LiquidityAddConfirmState
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove.LiquidityRemoveState
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremoveconfirm.LiquidityRemoveConfirmState
import jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails.PoolDetailsState
import jp.co.soramitsu.liquiditypools.impl.presentation.poollist.PoolListState
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.liquiditypools.navigation.NavAction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.math.BigDecimal
import java.time.Duration

/** The flow fragment's composable content (toolbar, NavHost, actions) with a stand-in view model. */
@RunWith(RobolectricTestRunner::class)
class PoolsFlowFragmentContentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ids = StringPair("xor", "val")
    private val routes = MutableStateFlow<LiquidityPoolsNavGraphRoute>(LiquidityPoolsNavGraphRoute.Loading)
    private val actions = MutableSharedFlow<NavAction>(extraBufferCapacity = 8)
    private val toolbar = MutableStateFlow<LoadingState<TextModel>>(LoadingState.Loading())
    private val stubViewModel = mockk<PoolsFlowViewModel>(relaxed = true) {
        every { navGraphRoutesFlow } returns routes
        every { navGraphActionsFlow } returns actions
        every { toolbarStateFlow } returns toolbar
        every {
            allPoolsScreenState
        } returns MutableStateFlow(AllPoolsState(allPools = listOf(pool("XOR-ALL")), isLoading = false))
        every {
            poolListScreenState
        } returns MutableStateFlow(PoolListState(pools = listOf(pool("XOR-LIST")), isLoading = false))
        every { poolDetailsScreenState } returns MutableStateFlow(PoolDetailsState(tvl = "$100"))
        every { liquidityAddScreenState } returns MutableStateFlow(LiquidityAddState(feeInfo = fee("0.0101 XOR")))
        every { liquidityAddConfirmState } returns MutableStateFlow(LiquidityAddConfirmState(feeInfo = fee("0.0102 XOR")))
        every { liquidityRemoveScreenState } returns MutableStateFlow(LiquidityRemoveState(feeInfo = fee("0.0103 XOR")))
        every { liquidityRemoveConfirmState } returns MutableStateFlow(LiquidityRemoveConfirmState(feeInfo = fee("0.0104 XOR")))
    }

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    private fun show() {
        val fragment = PoolsFlowFragment().apply {
            PoolsFlowFragment::class.java.getDeclaredField("viewModel\$delegate").also { it.isAccessible = true }
                .set(this, lazyOf(stubViewModel))
        }
        compose.setContent { FearlessAppTheme { fragment.Content(PaddingValues()) } }
        compose.waitForIdle()
    }

    private fun open(route: LiquidityPoolsNavGraphRoute, text: String) {
        routes.value = route
        compose.waitForIdle()
        compose.onNodeWithText(text).assertExists()
        verify { stubViewModel.onDestinationChanged(route.routeName) }
    }

    @Test
    fun everyRouteOpensItsScreenAndReportsTheDestination() {
        show()
        verify { stubViewModel.onDestinationChanged(LiquidityPoolsNavGraphRoute.Loading.routeName) }

        open(LiquidityPoolsNavGraphRoute.AllPoolsScreen(), "XOR-ALL")
        open(LiquidityPoolsNavGraphRoute.ListPoolsScreen(isUserPools = false), "XOR-LIST")
        open(LiquidityPoolsNavGraphRoute.PoolDetailsScreen(ids), "$100")
        open(LiquidityPoolsNavGraphRoute.LiquidityAddScreen(ids), "0.0101 XOR")
        open(LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen(ids, BigDecimal.ONE, BigDecimal.TEN, "1%"), "0.0102 XOR")
        open(LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen(ids), "0.0103 XOR")
        open(
            LiquidityPoolsNavGraphRoute.LiquidityRemoveConfirmScreen(ids, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE),
            "0.0104 XOR"
        )
    }

    @Test
    fun backActionsPopPagesUntilTheFirstPageClosesTheFlow() {
        show()
        open(LiquidityPoolsNavGraphRoute.AllPoolsScreen(), "XOR-ALL")
        open(LiquidityPoolsNavGraphRoute.PoolDetailsScreen(ids), "$100")

        actions.tryEmit(NavAction.BackPressed)
        compose.waitForIdle()
        compose.onNodeWithText("XOR-ALL").assertExists()
        verify(exactly = 0) { stubViewModel.exitFlow() }

        actions.tryEmit(NavAction.BackPressed)
        compose.waitForIdle()
        verify(exactly = 1) { stubViewModel.exitFlow() }

        actions.tryEmit(NavAction.SupplyLiquidityCompleted)
        compose.waitForIdle()
        verify { stubViewModel.supplyLiquidityCompleted() }
    }

    @Test
    fun toolbarShowsTheLogoOrThePageTitleAndBothNavigateBack() {
        show()

        toolbar.value = LoadingState.Loaded(TextModel.SimpleString(""))
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription(context.getString(jp.co.soramitsu.common.R.string.ux_close))[0].performClick()
        verify(exactly = 1) { stubViewModel.onNavigationClick() }

        toolbar.value = LoadingState.Loaded(TextModel.ResId(R.string.lp_pool_details_title))
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.lp_pool_details_title)).assertExists()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithContentDescription(context.getString(jp.co.soramitsu.common.R.string.ux_back)).performClick()
        verify(exactly = 2) { stubViewModel.onNavigationClick() }

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        verify(exactly = 3) { stubViewModel.onNavigationClick() }
    }

    private fun pool(name: String) = BasicPoolListItemState(
        ids = StringPair(name, "target"), token1Icon = "", token2Icon = "", text1 = name, text2 = "$1", apy = LoadingState.Loaded("1%")
    )

    private fun fee(amount: String) = FeeInfoViewState(feeAmount = amount, feeAmountFiat = null)
}
