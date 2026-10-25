package jp.co.soramitsu.liquiditypools.impl.navigation

import io.mockk.mockk
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.liquiditypools.navigation.NavAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InternalPoolsRouterTest {
    @Test
    fun `actions emitted without a collector are not replayed into a later screen`() = runTest {
        val router = InternalPoolsRouterImpl(mockk(), mockk(), backgroundScope)
        router.back()
        router.supplyLiquidityCompleted()

        val action = withTimeoutOrNull(100) { router.createNavGraphActionsFlow().first() }

        assertNull(action)
    }

    @Test
    fun `confirmation back burst reaches a scheduled collector in order`() = runTest {
        val router = InternalPoolsRouterImpl(mockk(), mockk(), backgroundScope)
        val actions = mutableListOf<NavAction>()
        backgroundScope.launch { router.createNavGraphActionsFlow().take(4).toList(actions) }
        runCurrent()
        router.openAllPoolsScreen()
        repeat(3) { router.openPoolListScreen(true) }

        repeat(3) { router.back() }
        router.supplyLiquidityCompleted()
        runCurrent()

        assertEquals(
            listOf(NavAction.BackPressed, NavAction.BackPressed, NavAction.BackPressed, NavAction.SupplyLiquidityCompleted),
            actions
        )
        assertNull(router.destination(LiquidityPoolsNavGraphRoute.ListPoolsScreen::class.java))
        assertNotNull(router.destination(LiquidityPoolsNavGraphRoute.AllPoolsScreen::class.java))
    }

    @Test
    fun `initial route survives collector startup`() = runTest {
        val router = InternalPoolsRouterImpl(mockk(), mockk(), backgroundScope)
        router.openAllPoolsScreen()

        val route = withTimeoutOrNull(100) { router.createNavGraphRoutesFlow().first() }

        assertTrue(route is LiquidityPoolsNavGraphRoute.AllPoolsScreen)
    }

    @Test
    fun `multiple route collectors do not duplicate back stack`() = runTest {
        val router = InternalPoolsRouterImpl(mockk(), mockk(), backgroundScope)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        repeat(2) {
            backgroundScope.launch(dispatcher) { router.createNavGraphRoutesFlow().collect() }
        }
        backgroundScope.launch(dispatcher) { router.createNavGraphActionsFlow().collect() }
        router.openAllPoolsScreen()
        router.openPoolListScreen(true)

        router.back()

        assertNull(router.destination(LiquidityPoolsNavGraphRoute.ListPoolsScreen::class.java))
        assertNotNull(router.destination(LiquidityPoolsNavGraphRoute.AllPoolsScreen::class.java))
    }

    @Test
    fun `multiple action collectors pop one destination per back`() = runTest {
        val router = InternalPoolsRouterImpl(mockk(), mockk(), backgroundScope)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(dispatcher) { router.createNavGraphRoutesFlow().collect() }
        repeat(2) {
            backgroundScope.launch(dispatcher) { router.createNavGraphActionsFlow().collect() }
        }
        router.openAllPoolsScreen()
        router.openPoolListScreen(true)

        router.back()

        assertNull(router.destination(LiquidityPoolsNavGraphRoute.ListPoolsScreen::class.java))
        assertNotNull(router.destination(LiquidityPoolsNavGraphRoute.AllPoolsScreen::class.java))
    }
}
