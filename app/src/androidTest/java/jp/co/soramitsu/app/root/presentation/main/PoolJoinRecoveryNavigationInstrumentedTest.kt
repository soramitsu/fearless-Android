package jp.co.soramitsu.app.root.presentation.main

import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavDestination
import androidx.navigation.Navigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.navigation.Navigator as AppNavigator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual shipped graph XML and application router, with AndroidX test destinations instead of wallet fragments. */
@RunWith(AndroidJUnit4::class)
@UiThreadTest
class PoolJoinRecoveryNavigationInstrumentedTest {
    @Test fun poolRecoveryOpensInsideDefiAndReturnsToItsStakingCaller() {
        val root = controller(R.navigation.main_nav_graph)
        val tabs = controller(R.navigation.bottom_nav_graph)
        assertTrue(selectMainTab(tabs, R.id.defiGraph))
        tabs.navigate(R.id.stakingFragment)
        val navigator = AppNavigator(jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAccess()).apply { attach(root, AppCompatActivity()); attachMainTabs(tabs) }
        navigator.openPoolJoinRecovery()
        assertEquals(R.id.poolJoinRecoveryFragment, tabs.currentDestination?.id)
        assertEquals(R.id.stakingFragment, tabs.previousBackStackEntry?.destination?.id)
        assertEquals(R.id.mainFragment, root.currentDestination?.id)
        navigator.back()
        assertEquals(R.id.stakingFragment, tabs.currentDestination?.id)
        assertEquals(R.id.mainFragment, root.currentDestination?.id)
    }

    @Test fun rootGraphRecoveryRemainsReachableWhenTabsAreNotMounted() {
        val root = controller(R.navigation.main_nav_graph)
        val navigator = AppNavigator(jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAccess()).apply { attach(root, AppCompatActivity()) }
        navigator.openPoolJoinRecovery()
        assertEquals(R.id.poolJoinRecoveryFragment, root.currentDestination?.id)
        navigator.back()
        assertEquals(R.id.mainFragment, root.currentDestination?.id)
    }

    private fun controller(graph: Int): TestNavHostController {
        val result = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
        val test = result.navigatorProvider.getNavigator<Navigator<NavDestination>>("test")
        // Inflate the actual graphs while suppressing fragment creation and its unrelated wallet services.
        for (name in listOf("fragment", "dialog", "activity")) result.navigatorProvider.addNavigator(name, test)
        result.setGraph(graph)
        return result
    }
}
