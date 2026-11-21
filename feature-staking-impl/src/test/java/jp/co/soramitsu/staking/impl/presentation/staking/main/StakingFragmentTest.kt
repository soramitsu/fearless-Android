package jp.co.soramitsu.staking.impl.presentation.staking.main

import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.Duration
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.AssetSelectorState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.StakingStoryModel
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.HiltHostApplication
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.EstimatedEarningsViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakeInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakeStatus
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakingAssetInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.view.NetworkInfoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltHostApplication::class)
class StakingFragmentTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val vm = mockk<StakingViewModel>(relaxed = true)
    private val nav = mockk<NavController>(relaxed = true)
    private val owner = mockk<NavBackStackEntry>(relaxed = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val type = MutableStateFlow<StakingType?>(StakingType.RELAYCHAIN)
    private val network = MutableStateFlow<LoadingState<StakingNetworkInfoModel>>(LoadingState.Loading())
    private val alerts = MutableStateFlow<LoadingState<List<AlertModel>>>(LoadingState.Loaded(emptyList()))
    private val state = MutableStateFlow<StakingScreenViewState?>(null)
    private val manage = MutableLiveData<Event<ManageStakingBottomSheet.Payload>?>()
    private var fragment: StakingFragment? = null
    private val value = TitleValueViewState("Staked", "10 DOT")
    private val earnings = EstimatedEarningsViewState(
        TitleValueViewState("Monthly", "1 DOT"), TitleValueViewState("Yearly", "12 DOT"),
        AmountInputViewState("DOT", "", "100", null, BigDecimal.TEN, null)
    )

    @Before
    fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        every { nav.getBackStackEntry(R.id.stakingFragment) } returns owner
        every { nav.currentBackStackEntry } returns owner
        every { vm.state } returns state
        every { vm.isInputFocused } returns MutableStateFlow(false)
        every { vm.stakingTypeFlow } returns type
        every { vm.networkInfo } returns network
        every { vm.alertsFlow } returns alerts
        every { vm.stakingStateScope } returns scope
        every { vm.stories } returns MutableStateFlow(emptyList())
        every { vm.networkInfoTitle } returns MutableStateFlow("Polkadot")
        every { vm.currentAddressModelLiveData } returns MutableLiveData()
        every { vm.showManageStakeEvent } returns manage
        every { vm.showRewardEstimationEvent } returns MutableLiveData()
        every { vm.showStakingStatusEvent } returns MutableLiveData()
        every { vm.assetSelectorMixin.showAssetChooser } returns MutableLiveData()
    }

    @After
    fun tearDown() {
        HiltFragmentHarness.closeHosts()
        scope.cancel()
    }

    private fun launch(): StakingFragment {
        val result = StakingFragment()
        HiltFragmentHarness.launch(result, vm) {
            it.imageLoader = mockk(relaxed = true)
            Navigation.setViewNavController(it.requireActivity().findViewById(android.R.id.content), nav)
        }
        fragment = result
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        return result
    }

    private fun settle() {
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun screen(staking: StakingViewState) = StakingScreenViewState(
        AssetSelectorState("DOT staking", "", "100 DOT"),
        StakingAssetInfoViewState.RelayChain("Network", "", value, value, value, value), staking
    )

    private fun clickText(text: String) {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(text, ignoreCase = true).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    @Test
    fun loadingAndMissingSelectionsShowOnlyTheApplicableNetwork() {
        val root = launch().requireView()
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.stakingNetworkInfo).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.parachainStakingNetworkInfo).visibility)
        type.value = StakingType.PARACHAIN
        settle()
        assertEquals(View.GONE, root.findViewById<View>(R.id.stakingNetworkInfo).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.parachainStakingNetworkInfo).visibility)
        type.value = StakingType.POOL
        settle()
        assertEquals(View.GONE, root.findViewById<View>(R.id.parachainStakingNetworkInfo).visibility)
        compose.onNodeWithText(root.resources.getString(R.string.pool_join_recovery_title), ignoreCase = true).assertExists()
        clickText(root.resources.getString(R.string.pool_join_recovery_title))
        val guard = slot<() -> Boolean>()
        verify { vm.onPoolJoinRecovery(capture(guard)) }
        assertTrue(guard.captured())
        type.value = null
        settle()
        assertEquals(View.GONE, root.findViewById<View>(R.id.stakingNetworkInfo).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.parachainStakingNetworkInfo).visibility)
    }

    @Test
    fun loadedNetworksDisplayAmountsAndUpdateFiatValues() {
        val root = launch().requireView()
        network.value = LoadingState.Loaded(StakingNetworkInfoModel.RelayChain("28 days", "250 DOT", "$500", "1M DOT", "$2M", "1234"))
        settle()
        assertTrue(texts(root).containsAll(listOf("28 days", "250 DOT", "$500", "1M DOT", "$2M", "1234")))
        network.value = LoadingState.Loaded(StakingNetworkInfoModel.RelayChain("7 days", "100 DOT", null, "2M DOT", null, "99"))
        settle()
        assertTrue(texts(root).containsAll(listOf("7 days", "100 DOT", "2M DOT", "99")))
        type.value = StakingType.PARACHAIN
        network.value = LoadingState.Loaded(StakingNetworkInfoModel.Parachain("2 days", "20 MOVR", "$40"))
        settle()
        val parachain = root.findViewById<View>(R.id.parachainStakingNetworkInfo)
        assertEquals(View.VISIBLE, parachain.visibility)
        assertTrue(texts(parachain).containsAll(listOf("2 days", "20 MOVR", "$40")))
        network.value = LoadingState.Loaded(StakingNetworkInfoModel.Parachain("3 days", "30 MOVR", null))
        settle()
        assertTrue(texts(parachain).containsAll(listOf("3 days", "30 MOVR")))
    }

    @Test
    fun alertsAvatarAndStoriesReflectTheViewModel() {
        val root = launch().requireView()
        assertEquals(View.GONE, root.findViewById<View>(R.id.stakingAlertsInfo).visibility)
        alerts.value = LoadingState.Loading()
        settle()
        alerts.value = LoadingState.Loaded(listOf(AlertModel(R.drawable.ic_info_16, "Claim rewards", "Ready to claim", AlertModel.Type.Info)))
        settle()
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.stakingAlertsInfo).visibility)
        assertTrue(texts(root).contains("Claim rewards"))
        root.findViewById<View>(R.id.stakingAvatar).performClick()
        val story = StakingStoryModel(R.string.staking_manage_title, "DOT", emptyList())
        root.findViewById<NetworkInfoView>(R.id.stakingNetworkInfo).storyClicked(story)
        root.findViewById<NetworkInfoView>(R.id.parachainStakingNetworkInfo).storyClicked(story)
        verify { vm.avatarClicked() }
        verify(exactly = 2) { vm.storyClicked(StoryGroupModel(emptyList())) }
        verify { vm.onStakingScreenResumed() }
    }

    @Test
    fun relayWelcomeForwardsStartAndInformationWithADestinationGuard() {
        state.value = screen(StakingViewState.RelayChain.Welcome(earnings))
        val f = launch()
        compose.onNodeWithText("DOT staking").assertExists()
        clickText(f.getString(R.string.staking_start_title))
        compose.onNodeWithTag("EstimatedEarningsTitle").performSemanticsAction(SemanticsActions.OnClick) { it() }
        val guard = slot<() -> Boolean>()
        verify { vm.startNonPoolStakingClick(capture(guard)) }
        verify { vm.onEstimatedEarningsInfoClick(any()) }
        assertTrue(guard.captured())
        every { nav.currentBackStackEntry } returns null
        assertFalse(guard.captured())
    }

    @Test
    fun parachainWelcomeStartsNonPoolStaking() {
        type.value = StakingType.PARACHAIN
        state.value = screen(StakingViewState.Parachain.Welcome(earnings))
        val f = launch()
        clickText(f.getString(R.string.staking_start_title))
        verify { vm.startNonPoolStakingClick(any()) }
    }

    @Test
    fun poolWelcomeRendersNetworkLimitsAndStartsPoolStaking() {
        type.value = StakingType.POOL
        state.value = screen(StakingViewState.Pool.Welcome(earnings)).copy(networkInfoState =
            StakingAssetInfoViewState.StakingPool("Pool network", "Pool guide", value, value, value, value, value))
        val f = launch()
        compose.onNodeWithText("Pool network").assertExists()
        clickText(f.getString(R.string.staking_start_title))
        verify { vm.startStakingPoolClick(any()) }
    }

    @Test
    fun relayStakeKeepsTheRenderedStateForManageAndStatusActions() {
        val stake = StakingViewState.RelayChain.Stake(
            StakeInfoViewState.RelayChainStakeInfoViewState("My relay stake", value, value, StakeStatus.Inactive("Era 42")),
            setOf(ManageStakeAction.BALANCE), StakingViewState.StatusInfo("Inactive", "Not elected")
        )
        state.value = screen(stake)
        val f = launch()
        compose.onNodeWithText("My relay stake").assertExists()
        clickText("My relay stake")
        clickText(f.getString(R.string.staking_nominator_status_inactive))
        verify { vm.onManageRelayChainStake(stake, any()) }
        verify { vm.onRelayChainStatusClick(stake, any()) }
    }

    @Test
    fun poolMemberManageUsesThePoolAction() {
        type.value = StakingType.POOL
        state.value = screen(StakingViewState.Pool.PoolMember(
            StakeInfoViewState.PoolStakeInfoViewState("My pool stake", value, value, value, value, StakeStatus.PoolHasNoValidators)))
        val f = launch()
        compose.onNodeWithText("My pool stake").assertExists()
        clickText("My pool stake")
        verify { vm.onManagePoolStake(any()) }
    }

    @Test
    fun managementEventsShowAvailableActionsAndDismissOnWithdrawal() {
        val f = launch()
        val payload = ManageStakingBottomSheet.Payload(setOf(ManageStakeAction.BALANCE))
        every { vm.isManagementPresentationCurrent(payload) } returns true
        manage.value = Event(payload)
        settle()
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        assertTrue(texts(dialog.window!!.decorView).contains(f.getString(R.string.staking_balance_title)))
        ((dialog.findViewById<View>(R.id.itemSheetStakingActionText).parent as View).parent as View).performClick()
        verify { vm.onManageStakeActionChosen(payload, ManageStakeAction.BALANCE) }
        manage.value = null
        settle()
        assertFalse(dialog.isShowing)
        verify { vm.onManagementPresentationDismissed(payload) }
        f.parentFragmentManager.beginTransaction().remove(f).commitNow()
        fragment = null
        verify { vm.onStakingScreenPaused() }
        verify { nav.removeOnDestinationChangedListener(any()) }
    }
}
