package jp.co.soramitsu.app.root.presentation.launch

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import io.mockk.mockk
import java.time.Duration
import jp.co.soramitsu.app.HiltShim
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.HiltShimHostActivity
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.app.root.presentation.main.MainFragment
import jp.co.soramitsu.app.root.presentation.main.MainViewModel
import jp.co.soramitsu.common.launch.LaunchFlightStage
import jp.co.soramitsu.common.launch.LaunchLanding
import jp.co.soramitsu.core.runtime.ChainConnection
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** How the wallet host wires the launch clip, the first-screen report and the unlock landing. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class LaunchIgniteHostTest {

    class Screen : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FrameLayout(inflater.context)
    }

    class Headless : Fragment()

    private class StandIns : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment =
            if (className == NavHostFragment::class.java.name) super.instantiate(classLoader, className) else Screen()
    }

    private val activity = Robolectric.buildActivity(HiltShimHostActivity::class.java).setup().get()
    private val content = FrameLayout(activity)

    @Before
    fun setUp() {
        LaunchLanding.resetForTest()
        LaunchFlightStage.resetForTest()
        activity.findViewById<ViewGroup>(android.R.id.content).addView(content)
    }

    @After
    fun tearDown() {
        LaunchLanding.resetForTest()
        LaunchFlightStage.resetForTest()
        HiltShim.reset()
    }

    @Test
    fun onlyTheFirstWalletHostOfAProcessGetsTheClip() {
        LaunchIgniteHost.attachIfColdStart(activity, content)
        assertEquals(1, overlays().size)
        assertEquals(0f, content.alpha)
        LaunchIgniteHost.attachIfColdStart(activity, content)
        assertEquals("A warm start shows the screen at once", 1, overlays().size)
    }

    @Test
    fun theFirstScreenAPersonCanUseReportsItself() {
        LaunchIgniteHost.reportScreens(activity.supportFragmentManager)
        activity.supportFragmentManager.beginTransaction().add(Headless(), "headless").commitNow()
        assertTrue(LaunchLanding.ready.value)

        LaunchLanding.resetForTest()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, Screen()).commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(LaunchLanding.ready.value)
    }

    @Test
    fun unlockingIntoTheWalletLandsItsTiers() {
        HiltShim.inject(MainFragment::class) { it.navigator = mockk(relaxed = true) }
        HiltShim.viewModel(MainViewModel::class) {
            MainViewModel(RootInteractor(), MutableStateFlow(ChainConnection.ExternalRequirement.FORBIDDEN))
        }
        activity.supportFragmentManager.fragmentFactory = StandIns()
        val root = mock(NavController::class.java)
        content.layout(0, 0, 720, 1560)
        LaunchIgniteHost.landAfterUnlock(activity, root, activity.supportFragmentManager, content)
        val listener = mockingDetails(root).invocations.single { it.method.name == "addOnDestinationChangedListener" }
            .arguments[0] as NavController.OnDestinationChangedListener
        listener.onDestinationChanged(root, destination(R.id.pincodeFragment), null)
        listener.onDestinationChanged(root, destination(R.id.mainFragment), null)

        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, MainFragment()).commitNow()
        assertTrue("The landing starts once the wallet screen has started", LaunchFlightStage.active)
        repeat(200) {
            if (!LaunchFlightStage.active) return@repeat
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        }
        assertFalse("The landing ends with every tier at rest", LaunchFlightStage.active)
    }

    @Test
    fun anUnlockThatEndsElsewhereDoesNotLand() {
        HiltShim.inject(MainFragment::class) { it.navigator = mockk(relaxed = true) }
        HiltShim.viewModel(MainViewModel::class) {
            MainViewModel(RootInteractor(), MutableStateFlow(ChainConnection.ExternalRequirement.FORBIDDEN))
        }
        activity.supportFragmentManager.fragmentFactory = StandIns()
        val root = mock(NavController::class.java)
        LaunchIgniteHost.landAfterUnlock(activity, root, activity.supportFragmentManager, content)
        val listener = mockingDetails(root).invocations.single { it.method.name == "addOnDestinationChangedListener" }
            .arguments[0] as NavController.OnDestinationChangedListener
        listener.onDestinationChanged(root, destination(R.id.pincodeFragment), null)
        listener.onDestinationChanged(root, destination(R.id.walletFragment), null)
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, MainFragment()).commitNow()
        assertFalse(LaunchFlightStage.active)
    }

    private fun overlays(): List<View> {
        val parent = activity.findViewById<ViewGroup>(android.R.id.content)
        return (0 until parent.childCount).map { parent.getChildAt(it) }.filterIsInstance<LaunchIgniteOverlay>()
    }

    private fun destination(id: Int): NavDestination = mock(NavDestination::class.java).also { `when`(it.id).thenReturn(id) }
}
