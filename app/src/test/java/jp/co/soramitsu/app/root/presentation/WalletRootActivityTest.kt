package jp.co.soramitsu.app.root.presentation

import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectService
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSnapshot
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import co.jp.soramitsu.walletconnect.model.WalletConnectEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.time.Duration
import jp.co.soramitsu.app.HiltShim
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.AppInitializer
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.app.root.domain.InitializeResult
import jp.co.soramitsu.app.root.domain.NotSupportedAppVersionException
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupResult
import jp.co.soramitsu.app.root.domain.WalletStartupSession
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.common.PLAY_MARKET_APP_URI
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealthTestHooks
import jp.co.soramitsu.common.launch.LaunchFlightStage
import jp.co.soramitsu.common.launch.LaunchLanding
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowToast
import jp.co.soramitsu.common.R as CommonR

/** The wallet host: it opens only for a ready startup session and wires the network bar, alerts and lifecycle. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class WalletRootActivityTest {
    @get:Rule val instantTasks = InstantTaskExecutorRule()

    class Screen : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FrameLayout(inflater.context)
    }

    private class StandIns : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment =
            if (className == NavHostFragment::class.java.name) super.instantiate(classLoader, className) else Screen()
    }

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val navigator = mockk<Navigator>(relaxed = true)
    private val initializer = mockk<AppInitializer>()
    private val interactor = mockk<RootInteractor>(relaxed = true)
    private val connections = MutableStateFlow(ChainConnection.ExternalRequirement.ALLOWED)
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "text-${firstArg<Int>()}" } }
    private val standIns = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
            (activity as? FragmentActivity)?.supportFragmentManager?.fragmentFactory = StandIns()
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
    private lateinit var controller: ActivityController<WalletRootActivity>
    private val activity get() = controller.get()

    @Before
    fun setUp() {
        LaunchLanding.resetForTest()
        LaunchFlightStage.resetForTest()
        WalletSecureStorageHealthTestHooks.reset()
        WalletStartupSession.resetForTest()
        mockkObject(WCDelegate)
        every { WCDelegate.registerDelegatesIfReady() } returns Result.success(Unit)
        every { WCDelegate.refreshConnections() } returns Unit
        every { WCDelegate.walletEvents } returns MutableSharedFlow<WalletConnectEvent>()
        coEvery { initializer.invoke(any()) } returns InitializeResult.Success
        app.registerActivityLifecycleCallbacks(standIns)
        HiltShim.reset()
        HiltShim.inject(WalletRootActivity::class) { it.navigator = navigator }
        HiltShim.viewModel(RootViewModel::class) {
            RootViewModel(initializer, interactor, mockk(relaxed = true), connections, resources,
                mockk(relaxed = true) { every { eventsFlow() } returns emptyFlow() },
                mockk<IrohaConnectService>(relaxed = true) { every { snapshot } returns MutableStateFlow(IrohaConnectSnapshot()) })
        }
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized && !activity.isDestroyed) controller.pause().stop().destroy()
        app.unregisterActivityLifecycleCallbacks(standIns)
        HiltShim.reset()
        unmockkObject(WCDelegate)
        WalletStartupSession.resetForTest()
        WalletSecureStorageHealthTestHooks.reset()
        LaunchLanding.resetForTest()
        LaunchFlightStage.resetForTest()
    }

    @Test
    fun withoutAReadyStartupTheHostHandsTheEntryBackToTheGate() {
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("fearless://buy-success")).putExtra("hostile", true))
        assertTrue(activity.isFinishing)
        val gate = shadowOf(activity).nextStartedActivity
        assertEquals(WalletStartupActivity::class.java.name, gate.component?.className)
        assertEquals(Uri.parse("fearless://buy-success"), gate.data)
        verify(exactly = 0) { navigator.attach(any(), any()) }
    }

    @Test
    fun aReadyHostShowsTheWalletGraphAndReportsCompletedPurchases() {
        every { interactor.isBuyProviderRedirectLink(any()) } returns true
        ready()
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("fearless://buy-success")))
        assertFalse(activity.isFinishing)
        verify(exactly = 1) { navigator.attach(any(), activity) }
        assertEquals("text-${R.string.buy_completed}", ShadowToast.getTextOfLatestToast())
        assertEquals(View.GONE, bar().visibility)
        assertEquals(Intent.ACTION_VIEW, activity.intent.action)
        assertTrue(activity.intent.extras == null)
    }

    @Test
    fun theNetworkBarFollowsTheConnection() {
        ready()
        launch(main())
        val connectivity = activity.getSystemService(ConnectivityManager::class.java)
        val callback = shadowOf(connectivity).networkCallbacks.single()
        callback.onLost(connectivity.activeNetwork ?: return)
        idle()
        assertEquals(View.VISIBLE, bar().visibility)
        assertEquals(activity.getString(R.string.network_status_connecting), bar().text.toString())

        callback.onCapabilitiesChanged(connectivity.activeNetwork!!, ShadowNetworkCapabilities.newInstance().apply {
            shadowOf(this).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        })
        idle()
        assertEquals(activity.getString(R.string.network_status_connected), bar().text.toString())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(View.GONE, bar().visibility)

        callback.onCapabilitiesChanged(connectivity.activeNetwork!!, ShadowNetworkCapabilities.newInstance())
        idle()
        assertEquals(View.VISIBLE, bar().visibility)
        callback.onAvailable(connectivity.activeNetwork!!)
        idle()
        assertEquals(activity.getString(R.string.network_status_connected), bar().text.toString())
    }

    @Test
    fun losingTheNetworkDuringRecoveryRestoresTheConnectingBar() {
        ready()
        launch(main())
        val connectivity = activity.getSystemService(ConnectivityManager::class.java)
        val callback = shadowOf(connectivity).networkCallbacks.single()
        val network = checkNotNull(connectivity.activeNetwork)
        callback.onLost(network)
        idle()
        callback.onAvailable(network)
        idle()
        assertEquals(View.VISIBLE, bar().visibility)
        assertEquals(activity.getString(R.string.network_status_connected), bar().text.toString())

        // Disconnect while the success message is still waiting to slide away.
        callback.onLost(network)
        idle()
        assertEquals(View.VISIBLE, bar().visibility)
        assertEquals(activity.getString(R.string.network_status_connecting), bar().text.toString())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals("The obsolete recovery animation must not hide a lost connection", View.VISIBLE, bar().visibility)
        assertEquals(activity.getString(R.string.network_status_connecting), bar().text.toString())
    }

    @Test
    fun anUnsupportedVersionOffersTheStore() {
        coEvery { initializer.invoke(any()) } returns InitializeResult.ErrorCanRetry(NotSupportedAppVersionException(), InitializationStep.AppConfig)
        ready()
        launch(main())
        val sheet = latestSheet()
        assertEquals(activity.getString(CommonR.string.update_needed_text), sheet.findViewById<TextView>(CommonR.id.alertTitle).text.toString())
        sheet.findViewById<View>(CommonR.id.alertConfirmButton).performClick()
        idle()
        assertEquals(Uri.parse(PLAY_MARKET_APP_URI), shadowOf(activity).nextStartedActivity.data)
    }

    @Test
    fun aFailedRefreshOffersARetryOfThatStep() {
        coEvery { initializer.invoke(InitializationStep.All) } returns InitializeResult.ReadyWithWarnings(listOf(InitializationStep.ChainsConfig))
        coEvery { initializer.invoke(InitializationStep.ChainsConfig) } returns InitializeResult.Success
        ready()
        launch(main())
        val sheet = latestSheet()
        assertEquals(
            activity.getString(CommonR.string.startup_network_refresh_unavailable),
            sheet.findViewById<TextView>(CommonR.id.alertMessage).text.toString()
        )
        sheet.findViewById<View>(CommonR.id.alertConfirmButton).performClick()
        idle()
        io.mockk.coVerify(exactly = 1) { initializer.invoke(InitializationStep.ChainsConfig) }
    }

    @Test
    fun lifecycleRevokesSavedEndpointAccessAndDetachesTheNavigator() {
        ready()
        launch(main())
        activity.onMoveToBackground()
        assertEquals(ChainConnection.ExternalRequirement.STOPPED, connections.value)
        activity.onMoveToForeground()
        assertEquals(ChainConnection.ExternalRequirement.ALLOWED, connections.value)
        val timer = RootViewModel::class.java.getDeclaredField("timerTask").apply { isAccessible = true }
        assertTrue(timer.get(activity.viewModel) == null)
        activity.findViewById<View>(R.id.root_touch_interceptor).dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        assertTrue("A touch restarts the inactivity timer", timer.get(activity.viewModel) != null)
        assertFalse(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN)))
        controller.pause().stop()
        verify(atLeast = 2) { navigator.revokeSavedEndpointAccess() }
        controller.destroy()
        verify(exactly = 1) { navigator.detach(activity) }
        assertEquals(ChainConnection.ExternalRequirement.FORBIDDEN, connections.value)
    }

    @Test
    fun newEntriesAreProcessedWhileReadyAndSentBackToTheGateOtherwise() {
        every { interactor.isBuyProviderRedirectLink(any()) } returns true
        ready()
        launch(main())
        controller.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse("fearless://buy-success")))
        assertEquals("text-${R.string.buy_completed}", ShadowToast.getTextOfLatestToast())
        assertEquals(Uri.parse("fearless://buy-success"), activity.intent.data)

        WalletStartupSession.resetForTest()
        controller.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://fearlesswallet.io/ton-connect/v2")))
        assertTrue(activity.isFinishing)
        assertEquals(WalletStartupActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun aSecureStorageFailureSendsTheLiveWalletBackToTheGate() {
        ready()
        launch(main())
        WalletSecureStorageHealthTestHooks.latchProcessRestartRequired()
        idle()
        assertTrue(activity.isFinishing)
        assertEquals(WalletStartupActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    private fun ready() = runBlocking {
        WalletStartupSession.openOrJoin { WalletDatabaseStartupResult.Ready }.await()
        WalletStartupSession.claimLatestPayload()
        Unit
    }

    private fun launch(intent: Intent) {
        controller = Robolectric.buildActivity(WalletRootActivity::class.java, intent.setClass(app, WalletRootActivity::class.java)).setup()
        idle()
    }

    private fun main() = Intent(Intent.ACTION_MAIN)
    private fun bar(): TextView = activity.findViewById(R.id.rootNetworkBar)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun latestSheet(): Dialog {
        idle()
        return checkNotNull(ShadowDialog.getLatestDialog()).also { assertTrue(it.isShowing) }
    }
}
