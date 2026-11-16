package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import org.junit.Before
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.FragmentActivity
import jp.co.soramitsu.runtime.multiNetwork.ChainState
import jp.co.soramitsu.runtime.multiNetwork.ChainsStateTracker
import jp.co.soramitsu.wallet.impl.WalletTestModels
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The developer service sheet lists every tracked network with its connection and runtime steps. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h3000dp")
class ServiceFragmentRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private var activityController: ActivityController<FragmentActivity>? = null

    @After
    fun tearDown() {
        val activity = activityController?.get()
        (activity?.supportFragmentManager?.findFragmentByTag("service") as? ServiceFragment)?.dismissNow()
        activityController?.pause()?.stop()?.destroy()
        ChainsStateTracker.state.value = emptyMap()
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun sheetShowsEachNetworksStatus() {
        val states = listOf(
            ChainState(WalletTestModels.chain(id = "a", name = "Alpha"), ChainState.ConnectionStatus.Connected("wss://a"), ChainState.Status.Completed, ChainState.Status.Started, ChainState.Status.Failed(IllegalStateException())),
            ChainState(WalletTestModels.chain(id = "b", name = "Beta"), ChainState.ConnectionStatus.Connecting("wss://b")),
            ChainState(WalletTestModels.chain(id = "c", name = "Gamma"), ChainState.ConnectionStatus.Paused("wss://c")),
            ChainState(WalletTestModels.chain(id = "d", name = "Delta"), ChainState.ConnectionStatus.Disconnected),
            ChainState(WalletTestModels.chain(id = "e", name = "Epsilon"), ChainState.ConnectionStatus.Failed)
        )
        compose.mainClock.autoAdvance = false
        ChainsStateTracker.state.value = states.associateBy { it.chain.id }
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).also {
            it.get().setTheme(jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)
        }.also { activityController = it }.setup().get()

        ServiceFragment().show(activity.supportFragmentManager, "service")
        activity.supportFragmentManager.executePendingTransactions()
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        compose.onNodeWithText("Alpha").assertExists()
        compose.onNodeWithText("Connected to wss://a").assertExists()
        compose.onNodeWithText("Connecting to wss://b").assertExists()
        compose.onNodeWithText("Paused wss://c").assertExists()
        compose.onNodeWithText("Disconnected").assertExists()
        assertTrue(activity.supportFragmentManager.findFragmentByTag("service") is ServiceFragment)
    }
}
