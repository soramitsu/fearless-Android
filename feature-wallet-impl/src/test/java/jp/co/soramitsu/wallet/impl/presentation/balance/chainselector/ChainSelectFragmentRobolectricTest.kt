package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment.Companion.KEY_CHOOSER_MODE
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment.Companion.KEY_CURRENCY_ID
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment.Companion.KEY_FILTERING_ENABLED
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment.Companion.KEY_FILTER_CHAIN_IDS
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment.Companion.KEY_SELECTED_ASSET_ID
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment.Companion.KEY_SELECTED_CHAIN_ID
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment.Companion.KEY_SELECT_ASSET
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment.Companion.KEY_SHOW_ALL_CHAINS
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment.Companion.KEY_XCM_ASSET_SYMBOL
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment.Companion.KEY_XCM_CHAIN_TYPE
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment.Companion.KEY_XCM_ORIGIN_ASSET_ID
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment.Companion.KEY_XCM_SELECTED_ORIGIN_CHAIN_ID
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The network picker sheet lists the view model's networks, reports a choice and its closing. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h1600dp")
class ChainSelectFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val viewModel = mockk<ChainSelectViewModel>(relaxed = true)
    private var host: ActivityController<StubHiltActivity>? = null

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        every { viewModel.state } returns MutableStateFlow<ChainSelectScreenContract.State>(
            ChainSelectScreenContract.State.Impl(
                chains = listOf(ChainSelectScreenContract.State.ItemState.Impl("kusama", null, "Kusama")),
                selectedChainId = null
            )
        )
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun sheetListsNetworksAndReportsTheChoice() {
        val fragment = ChainSelectFragment()
        host = hostSheet(fragment)

        compose.onNodeWithText(string(R.string.chain_selection_all_networks)).assertExists()
        compose.onNodeWithText("Kusama").performClick()
        settle()

        verify { viewModel.onChainSelected(match<ChainSelectScreenContract.State.ItemState> { it.id == "kusama" }) }
        assertTrue((fragment.dialog as BottomSheetDialog).behavior.skipCollapsed)
    }

    @Test
    fun dismissingTheSheetTellsTheViewModel() {
        val fragment = ChainSelectFragment()
        host = hostSheet(fragment)

        fragment.dismiss()
        ShadowLooper.idleMainLooper()

        verify { viewModel.onDialogClose() }
    }

    @Test
    fun assetBundleKeepsTheRequestedFlags() {
        val bundle = ChainSelectFragment.getBundle(
            assetId = "dot",
            chainId = "polkadot",
            chooserMode = true,
            isSelectAsset = false,
            isFilteringEnabled = true,
            showAllChains = true
        )

        assertEquals("dot", bundle.getString(KEY_SELECTED_ASSET_ID))
        assertEquals("polkadot", bundle.getString(KEY_SELECTED_CHAIN_ID))
        assertTrue(bundle.getBoolean(KEY_CHOOSER_MODE))
        assertFalse(bundle.getBoolean(KEY_SELECT_ASSET))
        assertTrue(bundle.getBoolean(KEY_FILTERING_ENABLED))
        assertTrue(bundle.getBoolean(KEY_SHOW_ALL_CHAINS))
    }

    @Test
    fun assetBundleDefaultsHideAllChainsAndSelectTheAsset() {
        val bundle = ChainSelectFragment.getBundle(assetId = "ksm")

        assertTrue(bundle.containsKey(KEY_SELECTED_CHAIN_ID))
        assertNull(bundle.getString(KEY_SELECTED_CHAIN_ID))
        assertFalse(bundle.getBoolean(KEY_CHOOSER_MODE))
        assertTrue(bundle.getBoolean(KEY_SELECT_ASSET))
        assertFalse(bundle.getBoolean(KEY_FILTERING_ENABLED))
        assertFalse(bundle.getBoolean(KEY_SHOW_ALL_CHAINS))
    }

    @Test
    fun filteredChooserBundleKeepsTheNetworksAndCurrency() {
        val networks = listOf("kusama", "moonriver")

        val bundle = ChainSelectFragment.getBundle(selectedChainId = "kusama", filterChainIds = networks, currencyId = "usd")

        assertEquals("kusama", bundle.getString(KEY_SELECTED_CHAIN_ID))
        @Suppress("DEPRECATION")
        val stored = bundle.get(KEY_FILTER_CHAIN_IDS)
        assertEquals(networks, stored)
        assertEquals("usd", bundle.getString(KEY_CURRENCY_ID))
        assertTrue(bundle.getBoolean(KEY_CHOOSER_MODE))
        assertTrue(bundle.getBoolean(KEY_SHOW_ALL_CHAINS))
        assertTrue(bundle.getBoolean(KEY_SELECT_ASSET))
        assertFalse(bundle.getBoolean(KEY_FILTERING_ENABLED))
    }

    @Test
    fun xcmBundleNeverSelectsAnAssetOrShowsAllChains() {
        val bundle = ChainSelectFragment.getBundleForXcmChains(
            selectedChainId = "polkadot",
            xcmChainType = XcmChainType.Destination,
            xcmSelectedOriginChainId = "kusama",
            xcmOriginAssetId = "ksm",
            xcmAssetSymbol = "KSM",
            isFilteringEnabled = true
        )

        assertEquals("polkadot", bundle.getString(KEY_SELECTED_CHAIN_ID))
        @Suppress("DEPRECATION")
        val type = bundle.get(KEY_XCM_CHAIN_TYPE)
        assertEquals(XcmChainType.Destination, type)
        assertEquals("kusama", bundle.getString(KEY_XCM_SELECTED_ORIGIN_CHAIN_ID))
        assertEquals("ksm", bundle.getString(KEY_XCM_ORIGIN_ASSET_ID))
        assertEquals("KSM", bundle.getString(KEY_XCM_ASSET_SYMBOL))
        assertFalse(bundle.getBoolean(KEY_SELECT_ASSET))
        assertFalse(bundle.getBoolean(KEY_SHOW_ALL_CHAINS))
        assertFalse(bundle.getBoolean(KEY_CHOOSER_MODE))
        assertTrue(bundle.getBoolean(KEY_FILTERING_ENABLED))
    }

    private fun hostSheet(fragment: ChainSelectFragment): ActivityController<StubHiltActivity> {
        val controller = HiltFragmentHost.host(fragment, mapOf(ChainSelectViewModel::class.java to viewModel))
        settle()
        return controller
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }
}
