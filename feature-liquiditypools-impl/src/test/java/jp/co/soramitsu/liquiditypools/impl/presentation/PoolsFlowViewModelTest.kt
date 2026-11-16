package jp.co.soramitsu.liquiditypools.impl.presentation

import androidx.compose.ui.unit.dp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.AllPoolsState
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd.LiquidityAddState
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm.LiquidityAddConfirmState
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove.LiquidityRemoveState
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremoveconfirm.LiquidityRemoveConfirmState
import jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails.PoolDetailsState
import jp.co.soramitsu.liquiditypools.impl.presentation.poollist.PoolListState
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.AllPoolsPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd.LiquidityAddPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm.LiquidityAddConfirmPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove.LiquidityRemovePresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremoveconfirm.LiquidityRemoveConfirmPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails.PoolDetailsPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.poollist.PoolListPresenter
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.NavAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PoolsFlowViewModelTest {

    private val routes = MutableSharedFlow<LiquidityPoolsNavGraphRoute>(replay = 1)
    private val actions = MutableSharedFlow<NavAction>(replay = 1)
    private val router = mockk<InternalPoolsRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns routes
        every { createNavGraphActionsFlow() } returns actions
    }
    private val poolsRouter = mockk<LiquidityPoolsRouter>(relaxed = true)
    private val allPools = mockk<AllPoolsPresenter>(relaxed = true)
    private val poolList = mockk<PoolListPresenter>(relaxed = true)
    private val details = mockk<PoolDetailsPresenter>(relaxed = true)
    private val add = mockk<LiquidityAddPresenter>(relaxed = true)
    private val addConfirm = mockk<LiquidityAddConfirmPresenter>(relaxed = true)
    private val remove = mockk<LiquidityRemovePresenter>(relaxed = true)
    private val removeConfirm = mockk<LiquidityRemoveConfirmPresenter>(relaxed = true)
    private val store = mockk<CoroutinesStore> { every { uiScope } returns CoroutineScope(Dispatchers.Unconfined) }
    private val allPoolsState = MutableStateFlow(AllPoolsState())
    private val poolListState = MutableStateFlow(PoolListState())
    private val detailsState = MutableStateFlow(PoolDetailsState())
    private val addState = MutableStateFlow(LiquidityAddState())
    private val removeState = MutableStateFlow(LiquidityRemoveState())
    private val addConfirmState = MutableStateFlow(LiquidityAddConfirmState())
    private val removeConfirmState = MutableStateFlow(LiquidityRemoveConfirmState())

    init {
        every { allPools.createScreenStateFlow(any()) } returns allPoolsState
        every { poolList.createScreenStateFlow(any()) } returns poolListState
        every { details.createScreenStateFlow(any()) } returns detailsState
        every { add.createScreenStateFlow(any()) } returns addState
        every { remove.createScreenStateFlow(any()) } returns removeState
        every { addConfirm.createScreenStateFlow(any()) } returns addConfirmState
        every { removeConfirm.createScreenStateFlow(any()) } returns removeConfirmState
    }

    private val viewModel = PoolsFlowViewModel(
        allPools, poolList, details, add, addConfirm, remove, removeConfirm, store, router, poolsRouter
    )

    @Test
    fun `the flow opens on all pools and mirrors routes and actions`() {
        verify { router.openAllPoolsScreen() }
        assertEquals(LiquidityPoolsNavGraphRoute.Loading, viewModel.navGraphRoutesFlow.value)

        routes.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityAddScreen(StringPair("a", "b")))
        actions.tryEmit(NavAction.BackPressed)

        assertEquals("LiquidityAddScreens", viewModel.navGraphRoutesFlow.value.routeName)
        assertEquals(listOf<NavAction>(NavAction.BackPressed), viewModel.navGraphActionsFlow.replayCache)
        assertTrue(viewModel.toolbarStateFlow.value is LoadingState.Loading)
    }

    @Test
    fun `screen states are the ones each presenter builds in the flow scope`() {
        val states = listOf(
            viewModel.allPoolsScreenState,
            viewModel.poolListScreenState,
            viewModel.poolDetailsScreenState,
            viewModel.liquidityAddScreenState,
            viewModel.liquidityRemoveScreenState,
            viewModel.liquidityAddConfirmState,
            viewModel.liquidityRemoveConfirmState
        )

        assertEquals(listOf(allPoolsState, poolListState, detailsState, addState, removeState, addConfirmState, removeConfirmState), states)
    }

    @Test
    fun `each destination sets its toolbar title`() {
        val titles = mapOf(
            LiquidityPoolsNavGraphRoute.PoolDetailsScreen.routeName to R.string.lp_pool_details_title,
            LiquidityPoolsNavGraphRoute.LiquidityAddScreen.routeName to R.string.lp_supply_liquidity_screen_title,
            LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen.routeName to R.string.lp_confirm_liquidity_screen_title,
            LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen.routeName to R.string.lp_remove_liquidity_screen_title,
            LiquidityPoolsNavGraphRoute.LiquidityRemoveConfirmScreen.routeName to R.string.lp_remove_liquidity_screen_title
        )

        titles.forEach { (route, title) ->
            viewModel.onDestinationChanged(route)
            assertEquals(route, TextModel.ResId(title), loadedTitle())
        }
        viewModel.onDestinationChanged(LiquidityPoolsNavGraphRoute.AllPoolsScreen.routeName)
        assertEquals(TextModel.SimpleString(""), loadedTitle())
        viewModel.onDestinationChanged(LiquidityPoolsNavGraphRoute.Loading.routeName)
        assertTrue(viewModel.toolbarStateFlow.value is LoadingState.Loading)
        viewModel.onDestinationChanged("unknown")
        assertTrue(viewModel.toolbarStateFlow.value is LoadingState.Loading)
    }

    @Test
    fun `pool lists are titled by whose pools they show`() {
        every { router.destination(LiquidityPoolsNavGraphRoute.ListPoolsScreen::class.java) } returnsMany listOf(
            LiquidityPoolsNavGraphRoute.ListPoolsScreen(isUserPools = true),
            LiquidityPoolsNavGraphRoute.ListPoolsScreen(isUserPools = false),
            null
        )

        val titles = List(3) {
            viewModel.onDestinationChanged(LiquidityPoolsNavGraphRoute.ListPoolsScreen.routeName)
            loadedTitle()
        }

        assertEquals(
            listOf(R.string.lp_user_pools_title, R.string.lp_available_pools_title, R.string.lp_available_pools_title)
                .map { TextModel.ResId(it) },
            titles
        )
    }

    @Test
    fun `returning to pool details or completing a supply clears the add inputs`() {
        viewModel.onDestinationChanged(LiquidityPoolsNavGraphRoute.PoolDetailsScreen.routeName)
        viewModel.supplyLiquidityCompleted()

        verify(exactly = 2) { add.resetInputFields() }
    }

    @Test
    fun `screen callbacks reach their presenters`() {
        val ids = StringPair("a", "b")

        viewModel.onPoolClicked(ids)
        viewModel.onAddReviewClick()
        viewModel.onAddBaseAmountChange(BigDecimal.ONE)
        viewModel.onAddTargetAmountChange(BigDecimal.TEN)
        viewModel.onAddBaseAmountFocusChange(true)
        viewModel.onAddTargetAmountFocusChange(true)
        viewModel.onAddTableItemClick(1)
        viewModel.onConfirmClick()
        viewModel.onAddItemClick(2)
        viewModel.onMoreClick(true)
        viewModel.onRefresh()
        // Dp is an inline class, which MockK cannot match in verification; the calls only must not fail.
        viewModel.onWindowHeightChange(10.dp)
        viewModel.onHeaderHeightChange(5.dp)
        viewModel.onAssetSearchEntered("xor")
        viewModel.onSupplyLiquidityClick()
        viewModel.onRemoveLiquidityClick()
        viewModel.onDetailItemClick(1)
        viewModel.onRemoveReviewClick()
        viewModel.onRemoveBaseAmountChange(BigDecimal.ONE)
        viewModel.onRemoveTargetAmountChange(BigDecimal.TEN)
        viewModel.onRemoveBaseAmountFocusChange(true)
        viewModel.onRemoveTargetAmountFocusChange(false)
        viewModel.onRemoveItemClick(2)
        viewModel.onRemoveConfirmClick()
        viewModel.onRemoveConfirmItemClick(1)

        verify { router.openDetailsPoolScreen(ids) }
        verify {
            add.onAddReviewClick()
            add.onAddBaseAmountChange(BigDecimal.ONE)
            add.onAddTargetAmountChange(BigDecimal.TEN)
            add.onAddBaseAmountFocusChange(true)
            add.onAddTargetAmountFocusChange(true)
            add.onAddTableItemClick(1)
            addConfirm.onConfirmClick()
            addConfirm.onAddItemClick(2)
            allPools.onMoreClick(true)
            allPools.onRefresh()
            poolList.onAssetSearchEntered("xor")
            details.onSupplyLiquidityClick()
            details.onRemoveLiquidityClick()
            details.onDetailItemClick(1)
            remove.onRemoveReviewClick()
            remove.onRemoveBaseAmountChange(BigDecimal.ONE)
            remove.onRemoveTargetAmountChange(BigDecimal.TEN)
            remove.onRemoveBaseAmountFocusChange(true)
            remove.onRemoveTargetAmountFocusChange(false)
            remove.onRemoveItemClick(2)
            removeConfirm.onRemoveConfirmClick()
            removeConfirm.onRemoveConfirmItemClick(1)
        }
        verify(exactly = 0) { allPools.onPoolClicked(any()) }
    }

    private fun loadedTitle() = (viewModel.toolbarStateFlow.value as LoadingState.Loaded).data
}
