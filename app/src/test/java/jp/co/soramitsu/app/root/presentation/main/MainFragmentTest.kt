package jp.co.soramitsu.app.root.presentation.main

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.app.HiltShim
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.HiltShimHostActivity
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.core.runtime.ChainConnection
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** The authenticated shell: its tab bar drives the nested tab graph (tab screens are stand-ins here). */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class MainFragmentTest {

    /** Tab screens belong to their feature modules; the shell is tested with empty stand-ins. */
    class StandInScreen : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FrameLayout(inflater.context)
    }

    private class StandIns : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment =
            if (className == NavHostFragment::class.java.name) super.instantiate(classLoader, className) else StandInScreen()
    }

    private val navigator = mockk<Navigator>(relaxed = true)
    private val connections = MutableStateFlow(ChainConnection.ExternalRequirement.FORBIDDEN)
    private lateinit var host: ActivityController<HiltShimHostActivity>
    private lateinit var fragment: MainFragment

    @Before
    fun setUp() {
        HiltShim.reset()
        HiltShim.inject(MainFragment::class) { it.navigator = navigator }
        HiltShim.viewModel(MainViewModel::class) { MainViewModel(RootInteractor(), connections) }
        host = Robolectric.buildActivity(HiltShimHostActivity::class.java).setup()
        host.get().supportFragmentManager.fragmentFactory = StandIns()
    }

    @After
    fun tearDown() {
        if (!host.get().isDestroyed) host.pause().stop().destroy()
        HiltShim.reset()
    }

    @Test
    fun theShellOpensOnThePortfolioAndAllowsChainConnections() {
        show()
        assertEquals(R.id.walletFragment, tabs().currentDestination?.id)
        assertEquals(ChainConnection.ExternalRequirement.ALLOWED, connections.value)
        verify(exactly = 1) { navigator.attachMainTabs(tabs()) }
        assertFalse("Back leaves the app from a tab root", host.get().onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun tabsSwitchFromTheBarAndBackReturnsToTheTabRoot() {
        show()
        bar().selectedItemId = R.id.defiGraph
        idle()
        assertEquals(R.id.defiHubFragment, tabs().currentDestination?.id)

        tabs().navigate(R.id.stakingFragment)
        idle()
        assertTrue(host.get().onBackPressedDispatcher.hasEnabledCallbacks())
        host.get().onBackPressedDispatcher.onBackPressed()
        idle()
        assertEquals(R.id.defiHubFragment, tabs().currentDestination?.id)

        tabs().navigate(R.id.stakingFragment)
        bar().selectedItemId = R.id.defiGraph
        idle()
        assertEquals("Reselecting a tab returns to its root", R.id.defiHubFragment, tabs().currentDestination?.id)
    }

    @Test
    fun theRaisedButtonOpensPolkaswapAndMarksItself() {
        show()
        val fab = fragment.requireView().findViewById<View>(R.id.fabMain)
        fab.performClick()
        idle()
        assertEquals(R.id.polkaswapHubFragment, tabs().currentDestination?.id)
        assertTrue(fab.isSelected)
        bar().selectedItemId = R.id.portfolioGraph
        idle()
        assertFalse(fab.isSelected)
    }

    @Test
    fun aMarketLinkOpensPolkamarktInTheDefiTab() {
        show(bundleOf("marketId" to "market-7"))
        assertEquals(R.id.polkamarktFragment, tabs().currentDestination?.id)
        assertEquals("market-7", tabs().currentBackStackEntry?.arguments?.getString("marketId"))
        assertEquals(R.id.defiGraph, bar().selectedItemId)
    }

    @Test
    fun leavingTheShellDetachesItsTabs() {
        show()
        val tabs = tabs()
        host.get().supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        verify(exactly = 1) { navigator.detachMainTabs(tabs) }
    }

    private fun show(arguments: Bundle? = null) {
        fragment = MainFragment().also { it.arguments = arguments }
        host.get().supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        idle()
    }

    private fun tabs(): NavController =
        (fragment.childFragmentManager.findFragmentById(R.id.bottomNavHost) as NavHostFragment).navController

    private fun bar(): BottomNavigationView = fragment.requireView().findViewById(R.id.bottom_navigation_view_with_fab)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
