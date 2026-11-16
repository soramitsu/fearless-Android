package jp.co.soramitsu.liquiditypools.impl.navigation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.liquiditypools.navigation.NavAction
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class InternalPoolsRouterRoutesTest {

    private val walletRouter = mockk<WalletRouter>(relaxed = true)
    private val resources = mockk<ResourceManager> {
        every { getString(R.string.lp_apy_title) } returns "APY"
        every { getString(R.string.lp_apy_alert_text) } returns "Strategic bonus"
        every { getString(R.string.common_network_fee) } returns "Network fee"
        every { getString(R.string.lp_network_fee_alert_text) } returns "Paid in XOR"
    }
    private val ids = StringPair("xor", "val")

    @Test
    fun `every screen route is published with its arguments and kept for destination lookups`() = runTest {
        val router = InternalPoolsRouterImpl(walletRouter, resources, backgroundScope)
        val routes = mutableListOf<LiquidityPoolsNavGraphRoute>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { router.createNavGraphRoutesFlow().toList(routes) }

        router.openAllPoolsScreen()
        router.openDetailsPoolScreen(ids)
        router.openAddLiquidityScreen(ids)
        router.openAddLiquidityConfirmScreen(ids, BigDecimal.ONE, BigDecimal.TEN, "12%")
        router.openRemoveLiquidityScreen(ids)
        router.openRemoveLiquidityConfirmScreen(ids, BigDecimal.ONE, BigDecimal.TEN, BigDecimal("0.9"), BigDecimal("9"), BigDecimal("3"))

        assertEquals(
            listOf("AllPoolsScreen", "LiquidityPoolDetailsScreen", "LiquidityAddScreens", "LiquidityAddConfirmScreen",
                "LiquidityRemoveScreen", "LiquidityRemoveConfirmScreen"),
            routes.map { it.routeName }
        )
        assertEquals(ids, router.destination(LiquidityPoolsNavGraphRoute.PoolDetailsScreen::class.java)?.ids)
        assertEquals("12%", router.destination(LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen::class.java)?.apy)
        assertEquals(
            BigDecimal("3"),
            router.destination(LiquidityPoolsNavGraphRoute.LiquidityRemoveConfirmScreen::class.java)?.desired
        )
    }

    @Test
    fun `popping to a screen drops everything above it and ignores unknown screens`() = runTest {
        val router = InternalPoolsRouterImpl(walletRouter, resources, backgroundScope)
        router.openAllPoolsScreen()
        router.openDetailsPoolScreen(ids)
        router.openAddLiquidityScreen(ids)

        router.popupToScreen(LiquidityPoolsNavGraphRoute.ListPoolsScreen)
        assertTrue(router.destination(LiquidityPoolsNavGraphRoute.LiquidityAddScreen::class.java) != null)

        router.popupToScreen(LiquidityPoolsNavGraphRoute.PoolDetailsScreen)
        assertNull(router.destination(LiquidityPoolsNavGraphRoute.LiquidityAddScreen::class.java))
        assertNull(router.destination(LiquidityPoolsNavGraphRoute.PoolDetailsScreen::class.java))
        assertTrue(router.destination(LiquidityPoolsNavGraphRoute.AllPoolsScreen::class.java) != null)
    }

    @Test
    fun `information and error dialogs are published as actions`() = runTest {
        val router = InternalPoolsRouterImpl(walletRouter, resources, backgroundScope)
        val actions = mutableListOf<NavAction>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { router.createNavGraphActionsFlow().toList(actions) }

        router.openInfoScreen(PoolsFlowViewModel.ITEM_APY_ID)
        router.openInfoScreen(PoolsFlowViewModel.ITEM_FEE_ID)
        router.openInfoScreen(itemId = 99)
        router.openErrorsScreen(title = null, message = "Pool unavailable")

        assertEquals(3, actions.size)
        val apy = actions[0] as NavAction.ShowInfo
        assertEquals("APY" to "Strategic bonus", apy.title to apy.message)
        val fee = actions[1] as NavAction.ShowInfo
        assertEquals("Network fee" to "Paid in XOR", fee.title to fee.message)
        val error = actions[2] as NavAction.ShowError
        assertNull(error.errorTitle)
        assertEquals("Pool unavailable", error.errorText)
    }

    @Test
    fun `success opens the wallet operation screen with the custom message`() = runTest {
        val router = InternalPoolsRouterImpl(walletRouter, resources, backgroundScope)

        router.openSuccessScreen("0xhash", soraMainChainId, "Liquidity added")

        verify { walletRouter.openOperationSuccess("0xhash", soraMainChainId, "Liquidity added", null) }
    }

    @Test
    fun `routes work with the default main-thread action scope`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val router = InternalPoolsRouterImpl(walletRouter, resources)

            router.openAllPoolsScreen()
            router.openPoolListScreen(isUserPools = false)
            router.back()

            assertNull(router.destination(LiquidityPoolsNavGraphRoute.ListPoolsScreen::class.java))
            assertTrue(router.destination(LiquidityPoolsNavGraphRoute.AllPoolsScreen::class.java) != null)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
