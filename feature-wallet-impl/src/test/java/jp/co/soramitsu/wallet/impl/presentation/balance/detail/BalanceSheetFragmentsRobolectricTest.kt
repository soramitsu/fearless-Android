package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.interfaces.AssetSorting
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.sort.AssetDetailsSortFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.sort.AssetDetailsSortViewModel
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.claimreward.ClaimRewardsFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.claimreward.ClaimRewardsViewModel
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.claimreward.ClaimRewardsViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FROZEN_ASSET_PAYLOAD
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenTokensFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenTokensViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The claim-rewards, frozen-tokens and asset-sort sheets render their state and forward their actions. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h2000dp")
class BalanceSheetFragmentsRobolectricTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val router = mockk<WalletRouter>(relaxed = true)
    private var host: ActivityController<StubHiltActivity>? = null

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @After
    fun closeHost() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun claimRewardsSheetShowsTheRewardAndForwardsTheClaim() {
        val viewModel = mockk<ClaimRewardsViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow(
                ClaimRewardsViewState(
                    chainIconUrl = null,
                    lockedInfoItem = TitleValueViewState("Locked", "5 DOT"),
                    tokenSymbol = "DOT",
                    buttonState = ButtonViewState("Claim", true)
                )
            )
            every { isClaimInProgress } returns MutableStateFlow(false)
            every { isSoftKeyboardOpenFlow } returns MutableStateFlow(false)
        }
        val fragment = ClaimRewardsFragment()
        host = HiltFragmentHost.host(fragment, mapOf(ClaimRewardsViewModel::class.java to viewModel))
        settle()

        compose.onNodeWithText("5 DOT").assertExists()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText("Claim").performClick()
        compose.waitForIdle()

        verify { viewModel.onNextClick() }
        // The sheet keeps the shared behaviour: expanded and not draggable.
        assertFalse((fragment.requireDialog() as BottomSheetDialog).behavior.isDraggable)
        assertEquals("polkadot", ClaimRewardsFragment.getBundle("polkadot").getString(ClaimRewardsFragment.KEY_CHAIN_ID))
    }

    @Test
    @Suppress("DEPRECATION")
    fun frozenTokensSheetListsEveryFrozenPart() {
        val payload = FrozenAssetPayload(
            locked = BigDecimal.ONE, staked = BigDecimal.TEN, reserved = BigDecimal("2"),
            redeemable = BigDecimal("3"), unstaking = BigDecimal("4"), assetSymbol = "dot"
        )
        val viewModel = FrozenTokensViewModel(SavedStateHandle(mapOf(FROZEN_ASSET_PAYLOAD to payload)))
        val fragment = FrozenTokensFragment()
        host = HiltFragmentHost.host(fragment, mapOf(FrozenTokensViewModel::class.java to viewModel))
        settle()

        compose.onNodeWithText(string(R.string.common_title_frozen_token, "DOT")).assertExists()
        compose.onNodeWithText(string(R.string.wallet_balance_bonded)).assertExists()
        compose.onNodeWithText(string(R.string.wallet_balance_unbonding_v1_9_0)).assertExists()
        val behavior = (fragment.requireDialog() as BottomSheetDialog).behavior
        assertTrue(
            "state ${behavior.state}",
            behavior.state == BottomSheetBehavior.STATE_EXPANDED || behavior.state == BottomSheetBehavior.STATE_SETTLING
        )
        assertEquals(payload, FrozenTokensFragment.getBundle(payload).get(FROZEN_ASSET_PAYLOAD))
    }

    @Test
    fun assetSortSheetStoresTheChosenSorting() {
        val sorting = MutableStateFlow(AssetSorting.Name)
        val interactor = mockk<WalletInteractor>(relaxed = true) { every { observeAssetSorting() } returns sorting }
        val resources = mockk<ResourceManager> { every { getString(R.string.common_sort_by) } returns "Sort by" }
        val viewModel = AssetDetailsSortViewModel(resources, interactor, router)
        val fragment = AssetDetailsSortFragment()
        host = HiltFragmentHost.host(fragment, mapOf(AssetDetailsSortViewModel::class.java to viewModel))
        settle()

        compose.onNodeWithText("Sort by").assertExists()
        compose.onNodeWithText(string(R.string.common_assets_filters_name)).assertExists()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.common_assets_filters_popularity)).performClick()
        compose.waitForIdle()

        verify { interactor.applyAssetSorting(AssetSorting.Popularity) }
        verify { router.back() }
        assertTrue((fragment.requireDialog() as BottomSheetDialog).behavior.skipCollapsed)
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }
}
