package jp.co.soramitsu.liquiditypools.navigation

import jp.co.soramitsu.androidfoundation.format.StringPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class LiquidityPoolsNavGraphRouteTest {

    private val ids = StringPair("0x02", "0x03")

    @Test
    fun `each screen route has a stable name shared with its companion`() {
        val routes = listOf(
            LiquidityPoolsNavGraphRoute.Loading to LiquidityPoolsNavGraphRoute.Loading,
            LiquidityPoolsNavGraphRoute.AllPoolsScreen() to LiquidityPoolsNavGraphRoute.AllPoolsScreen,
            LiquidityPoolsNavGraphRoute.ListPoolsScreen(true) to LiquidityPoolsNavGraphRoute.ListPoolsScreen,
            LiquidityPoolsNavGraphRoute.PoolDetailsScreen(ids) to LiquidityPoolsNavGraphRoute.PoolDetailsScreen,
            LiquidityPoolsNavGraphRoute.LiquidityAddScreen(ids) to LiquidityPoolsNavGraphRoute.LiquidityAddScreen,
            LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen(ids, BigDecimal.ONE, BigDecimal.TEN, "5%") to
                LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen,
            LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen(ids) to LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen,
            LiquidityPoolsNavGraphRoute.LiquidityRemoveConfirmScreen(
                ids, BigDecimal.ONE, BigDecimal.TEN, BigDecimal("0.9"), BigDecimal("9"), BigDecimal("2")
            ) to LiquidityPoolsNavGraphRoute.LiquidityRemoveConfirmScreen
        )

        routes.forEach { (route, companion) -> assertEquals(companion.routeName, route.routeName) }
        assertEquals(
            listOf(
                "Loading", "AllPoolsScreen", "ListPoolsScreen", "LiquidityPoolDetailsScreen", "LiquidityAddScreens",
                "LiquidityAddConfirmScreen", "LiquidityRemoveScreen", "LiquidityRemoveConfirmScreen"
            ),
            routes.map { it.first.routeName }
        )
    }

    @Test
    fun `routes keep the arguments of the screen they open`() {
        val confirm = LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen(ids, BigDecimal.ONE, BigDecimal.TEN, "5%")
        val remove = LiquidityPoolsNavGraphRoute.LiquidityRemoveConfirmScreen(
            ids, BigDecimal.ONE, BigDecimal.TEN, BigDecimal("0.9"), BigDecimal("9"), BigDecimal("2")
        )

        assertEquals(true, LiquidityPoolsNavGraphRoute.ListPoolsScreen(true).isUserPools)
        assertEquals(ids, LiquidityPoolsNavGraphRoute.PoolDetailsScreen(ids).ids)
        assertEquals(ids, LiquidityPoolsNavGraphRoute.LiquidityAddScreen(ids).ids)
        assertEquals(ids, LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen(ids).ids)
        assertEquals(listOf(ids, BigDecimal.ONE, BigDecimal.TEN, "5%"), listOf(confirm.ids, confirm.amountBase, confirm.amountTarget, confirm.apy))
        assertEquals(
            listOf(ids, BigDecimal.ONE, BigDecimal.TEN, BigDecimal("0.9"), BigDecimal("9"), BigDecimal("2")),
            listOf(remove.ids, remove.amountBase, remove.amountTarget, remove.firstAmountMin, remove.secondAmountMin, remove.desired)
        )
    }

    @Test
    fun `dialog actions carry their texts`() {
        val error = NavAction.ShowError(null, "Pool unavailable")
        val info = NavAction.ShowInfo("APY", "Strategic bonus")

        assertNull(error.errorTitle)
        assertEquals("Pool unavailable", error.errorText)
        assertEquals("APY" to "Strategic bonus", info.title to info.message)
        assertEquals("BackPressed", NavAction.BackPressed.toString())
        assertEquals("SupplyLiquidityCompleted", NavAction.SupplyLiquidityCompleted.toString())
    }
}
