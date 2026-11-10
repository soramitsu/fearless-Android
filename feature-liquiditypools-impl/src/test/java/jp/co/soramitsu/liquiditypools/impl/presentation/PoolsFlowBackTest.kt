package jp.co.soramitsu.liquiditypools.impl.presentation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.NavAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PoolsFlowBackTest {

    private val poolsRouter = mockk<LiquidityPoolsRouter>(relaxed = true)
    private val internalRouter = mockk<InternalPoolsRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns emptyFlow<LiquidityPoolsNavGraphRoute>()
        every { createNavGraphActionsFlow() } returns emptyFlow<NavAction>()
    }
    private val coroutinesStore = mockk<CoroutinesStore> {
        every { uiScope } returns CoroutineScope(Dispatchers.Unconfined)
    }

    private fun viewModel() = PoolsFlowViewModel(
        allPoolsPresenter = mockk(relaxed = true),
        poolListPresenter = mockk(relaxed = true),
        poolDetailsPresenter = mockk(relaxed = true),
        liquidityAddPresenter = mockk(relaxed = true),
        liquidityAddConfirmPresenter = mockk(relaxed = true),
        liquidityRemovePresenter = mockk(relaxed = true),
        liquidityRemoveConfirmPresenter = mockk(relaxed = true),
        coroutinesStore = coroutinesStore,
        internalPoolsRouter = internalRouter,
        poolsRouter = poolsRouter
    )

    @Test
    fun `back on a page goes through the flow's router, like the toolbar`() {
        viewModel().onNavigationClick()

        verify(exactly = 1) { internalRouter.back() }
        verify(exactly = 0) { poolsRouter.back() }
    }

    @Test
    fun `back presses queued as the flow closes leave only the sheet`() {
        val viewModel = viewModel()

        repeat(3) { viewModel.exitFlow() }

        verify(exactly = 1) { poolsRouter.back() }
    }

    @Test
    fun `system back reaches the flow before the NavHost and the sheet`() {
        val source = File("src/main/java/jp/co/soramitsu/liquiditypools/impl/presentation/PoolsFlowFragment.kt").readText()

        // The dispatcher runs the callback added last, and composition adds them in source order.
        val navHost = source.indexOf("NavHost(")
        val backHandler = source.indexOf("BackHandler(onBack = viewModel::onNavigationClick)")
        assertTrue(navHost >= 0)
        assertTrue(backHandler > navHost)
        // On Android 13+ a dialog's onBackPressed never runs: the sheet's own callback takes Back first.
        assertFalse(source.contains("onBackPressed"))
        assertTrue(source.contains("popFlowPageOrExit(LiquidityPoolsNavGraphRoute.Loading.routeName, viewModel::exitFlow)"))
    }
}
