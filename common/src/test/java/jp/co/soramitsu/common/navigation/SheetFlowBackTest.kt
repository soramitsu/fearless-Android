package jp.co.soramitsu.common.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetFlowBackTest {

    private val navController = mockk<NavController>(relaxed = true)
    private var exits = 0

    private fun back() = navController.popFlowPageOrExit(LOADING) { exits++ }

    private fun previousRouteIs(route: String?) {
        every { navController.previousBackStackEntry } returns route?.let { name ->
            mockk<NavBackStackEntry> {
                every { destination } returns mockk<NavDestination> { every { this@mockk.route } returns name }
            }
        }
    }

    @Test
    fun `back on a later page shows the page underneath`() {
        previousRouteIs("AllPools")

        back()

        verify(exactly = 1) { navController.popBackStack() }
        assertEquals(0, exits)
    }

    @Test
    fun `back on the first page leaves the flow without showing the placeholder`() {
        previousRouteIs(LOADING)

        back()

        verify(exactly = 0) { navController.popBackStack() }
        assertEquals(1, exits)
    }

    @Test
    fun `back with no page underneath leaves the flow`() {
        previousRouteIs(null)

        back()

        verify(exactly = 0) { navController.popBackStack() }
        assertEquals(1, exits)
    }

    private companion object {
        const val LOADING = "Loading"
    }
}
