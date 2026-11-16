package jp.co.soramitsu.app.root.presentation.main

import android.app.Application
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.Navigator
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tab selection on a real NavController whose graph mirrors the authenticated shell: one child graph per tab. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MainTabNavigationTest {

    @Navigator.Name("screen")
    private class ScreenNavigator : Navigator<NavDestination>() {
        override fun createDestination(): NavDestination = NavDestination(this)
    }

    private val controller = NavController(ApplicationProvider.getApplicationContext()).apply {
        val screens = ScreenNavigator()
        navigatorProvider.addNavigator(screens)
        val graphs = navigatorProvider.getNavigator(NavGraphNavigator::class.java)
        fun screen(id: Int) = NavDestination(screens).also { it.id = id }
        fun tab(id: Int, vararg leaves: Int) = NavGraph(graphs).also { tab ->
            tab.id = id
            leaves.forEach { tab.addDestination(screen(it)) }
            tab.setStartDestination(leaves.first())
        }
        graph = NavGraph(graphs).also { root ->
            root.id = ROOT
            root.addDestination(tab(R.id.portfolioGraph, R.id.walletFragment, R.id.manageAssetsFragment))
            root.addDestination(tab(R.id.defiGraph, R.id.defiHubFragment, R.id.stakingFragment))
            root.addDestination(tab(R.id.polkaswapGraph, R.id.polkaswapHubFragment))
            root.addDestination(screen(R.id.sendSetupFragment))
            root.setStartDestination(R.id.portfolioGraph)
        }
    }

    @Test
    fun selectingATabOpensItsStartScreenAndUnknownTabsAreRefused() {
        assertEquals(R.id.walletFragment, controller.currentDestination?.id)
        assertTrue(selectMainTab(controller, R.id.defiGraph))
        assertEquals(R.id.defiHubFragment, controller.currentDestination?.id)
        assertFalse(selectMainTab(controller, R.id.crossChainGraph))
        assertEquals(R.id.defiHubFragment, controller.currentDestination?.id)
        assertThrows(IllegalArgumentException::class.java) { selectMainTabOrThrow(controller, R.id.settingsGraph) }
    }

    @Test
    fun aLeafOfAnotherTabSelectsThatTabFirstSoBackReturnsToItsRoot() {
        val args = Bundle().apply { putString("chain", "polkadot") }
        navigateToMainTabDestination(controller, R.id.stakingFragment, args)
        assertEquals(R.id.stakingFragment, controller.currentDestination?.id)
        assertEquals("polkadot", controller.currentBackStackEntry?.arguments?.getString("chain"))
        assertEquals(R.id.defiHubFragment, controller.previousBackStackEntry?.destination?.id)

        assertTrue(reselectMainTab(controller, R.id.defiGraph))
        assertEquals(R.id.defiHubFragment, controller.currentDestination?.id)
    }

    @Test
    fun tabsRestoreTheirOwnStackWhenSelectedAgain() {
        navigateToMainTabDestination(controller, R.id.manageAssetsFragment)
        navigateToMainTabDestination(controller, R.id.polkaswapGraph)
        assertEquals(R.id.polkaswapHubFragment, controller.currentDestination?.id)
        selectMainTabOrThrow(controller, R.id.portfolioGraph)
        assertEquals(R.id.portfolioGraph, controller.currentDestination?.parent?.id)
        navigateToMainTabDestination(controller, R.id.walletFragment)
        assertEquals(R.id.walletFragment, controller.currentDestination?.id)
    }

    @Test
    fun globalDestinationsOpenOverTheCurrentTabAndForeignOnesAreRejected() {
        navigateToMainTabDestination(controller, R.id.sendSetupFragment)
        assertEquals(R.id.sendSetupFragment, controller.currentDestination?.id)
        assertThrows(IllegalArgumentException::class.java) {
            navigateToMainTabDestination(controller, R.id.profileFragment)
        }
    }

    @Test
    fun everyTabHasItsRootScreenForReselection() {
        assertEquals(R.id.walletFragment, rootDestinationForMainTab(R.id.portfolioGraph))
        assertEquals(R.id.defiHubFragment, rootDestinationForMainTab(R.id.defiGraph))
        assertEquals(R.id.polkaswapHubFragment, rootDestinationForMainTab(R.id.polkaswapGraph))
        assertEquals(R.id.crossChainFragment, rootDestinationForMainTab(R.id.crossChainGraph))
        assertEquals(R.id.profileFragment, rootDestinationForMainTab(R.id.settingsGraph))
        assertNull(rootDestinationForMainTab(R.id.sendSetupFragment))
        assertFalse(reselectMainTab(controller, R.id.sendSetupFragment))
        assertFalse("Nothing to pop on a tab that is not on the stack", reselectMainTab(controller, R.id.settingsGraph))
    }

    private companion object {
        const val ROOT = 0x7f7f0001
    }
}
