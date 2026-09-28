package jp.co.soramitsu.app.root.presentation.main

import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavDestination
import androidx.navigation.Navigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@UiThreadTest
class MainTabBackStackInstrumentedTest {

    @Test
    fun twoDeepStacksRestoreExactlyAndReselectReturnsToRoot() {
        val controller = mainTabController()

        controller.navigate(R.id.assetDetailFragment)
        controller.navigate(R.id.balanceDetailFragment)
        assertEquals(R.id.balanceDetailFragment, controller.currentDestination?.id)

        assertTrue(selectMainTab(controller, R.id.defiGraph))
        controller.navigate(R.id.stakingFragment)
        controller.navigate(R.id.setupStakingFragment)
        assertEquals(R.id.setupStakingFragment, controller.currentDestination?.id)

        assertTrue(selectMainTab(controller, R.id.portfolioGraph))
        assertEquals(R.id.balanceDetailFragment, controller.currentDestination?.id)

        assertTrue(reselectMainTab(controller, R.id.portfolioGraph))
        assertEquals(R.id.walletFragment, controller.currentDestination?.id)
    }

    @Test
    fun settingsToManageAssetsSelectsPortfolioAndPreservesItsStack() {
        val controller = mainTabController()
        controller.navigate(R.id.assetDetailFragment)
        assertTrue(selectMainTab(controller, R.id.settingsGraph))

        navigateToMainTabDestination(controller, R.id.manageAssetsFragment)

        assertEquals(R.id.manageAssetsFragment, controller.currentDestination?.id)
        assertTrue(controller.popBackStack())
        assertEquals(R.id.assetDetailFragment, controller.currentDestination?.id)
    }

    @Test
    fun polkaswapToLiquidityPoolsSelectsDefiBeforeNavigatingLeaf() {
        val controller = mainTabController()
        assertTrue(selectMainTab(controller, R.id.polkaswapGraph))

        navigateToMainTabDestination(controller, R.id.poolsFlowFragment)

        assertEquals(R.id.poolsFlowFragment, controller.currentDestination?.id)
        assertTrue(controller.currentDestination.isInGraphForTest(R.id.defiGraph))
    }

    @Test
    fun incomingSettingsLeafSelectsSettingsBeforeNavigatingRequest() {
        val controller = mainTabController()

        navigateToMainTabDestination(controller, R.id.requestPreviewFragment)

        assertEquals(R.id.requestPreviewFragment, controller.currentDestination?.id)
        assertTrue(controller.currentDestination.isInGraphForTest(R.id.settingsGraph))
    }

    @Test
    fun walletOptionsPreserveTheCallingTabAndSelectorBackStack() {
        for (tab in listOf(R.id.portfolioGraph, R.id.settingsGraph)) {
            val controller = mainTabController()
            assertTrue(selectMainTab(controller, tab))
            val originalDestination = controller.currentDestination?.id
            navigateToMainTabDestination(controller, R.id.selectWalletFragment)
            navigateToMainTabDestination(controller, R.id.optionsWalletFragment)
            assertEquals(R.id.optionsWalletFragment, controller.currentDestination?.id)
            for (action in listOf(R.id.accountDetailsDialog, R.id.backupWalletDialog, R.id.renameAccountDialog)) {
                navigateToMainTabDestination(controller, action)
                assertEquals(action, controller.currentDestination?.id)
                assertTrue(controller.popBackStack())
                assertEquals(R.id.optionsWalletFragment, controller.currentDestination?.id)
            }
            assertTrue(controller.popBackStack())
            assertEquals(R.id.selectWalletFragment, controller.currentDestination?.id)
            assertTrue(controller.popBackStack())
            assertEquals(originalDestination, controller.currentDestination?.id)
        }
    }

    @Test
    fun unknownAuthenticatedLeafThrowsInsteadOfBeingSwallowed() {
        val controller = mainTabController()

        assertThrows(IllegalArgumentException::class.java) {
            navigateToMainTabDestination(controller, Int.MAX_VALUE)
        }
    }

    private fun mainTabController(): TestNavHostController {
        val controller = TestNavHostController(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        val destinationNavigator = controller.navigatorProvider
            .getNavigator<Navigator<NavDestination>>("test")
        val graphNavigator = controller.navigatorProvider
            .getNavigator<NavGraphNavigator>("navigation")

        fun tabGraph(id: Int, root: Int, vararg children: Int): NavGraph {
            return NavGraph(graphNavigator).apply {
                this.id = id
                setStartDestination(root)
                (listOf(root) + children.toList()).forEach { destinationId ->
                    addDestination(destinationNavigator.createDestination().apply {
                        this.id = destinationId
                    })
                }
            }
        }

        controller.graph = NavGraph(graphNavigator).apply {
            id = R.id.bottom_nav_graph
            setStartDestination(R.id.portfolioGraph)
            for (id in listOf(R.id.selectWalletFragment, R.id.optionsWalletFragment, R.id.accountDetailsDialog, R.id.backupWalletDialog, R.id.renameAccountDialog)) {
                addDestination(destinationNavigator.createDestination().apply { this.id = id })
            }
            addDestination(
                tabGraph(
                    R.id.portfolioGraph,
                    R.id.walletFragment,
                    R.id.assetDetailFragment,
                    R.id.balanceDetailFragment,
                    R.id.manageAssetsFragment
                )
            )
            addDestination(
                tabGraph(
                    R.id.defiGraph,
                    R.id.defiHubFragment,
                    R.id.stakingFragment,
                    R.id.setupStakingFragment,
                    R.id.poolsFlowFragment
                )
            )
            addDestination(
                tabGraph(
                    R.id.polkaswapGraph,
                    R.id.polkaswapHubFragment,
                    R.id.swapTokensFragment
                )
            )
            addDestination(
                tabGraph(
                    R.id.settingsGraph,
                    R.id.profileFragment,
                    R.id.requestPreviewFragment
                )
            )
        }

        return controller
    }

    private fun NavDestination?.isInGraphForTest(graphId: Int): Boolean {
        var destination = this
        while (destination != null) {
            if (destination.id == graphId) return true
            destination = destination.parent
        }
        return false
    }
}
