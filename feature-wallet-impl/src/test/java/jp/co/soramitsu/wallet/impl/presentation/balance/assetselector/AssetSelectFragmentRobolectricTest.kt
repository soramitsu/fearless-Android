package jp.co.soramitsu.wallet.impl.presentation.balance.assetselector

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.presentation.AssetSelectPurpose
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.balance.assetselector.AssetSelectFragment.Companion.KEY_EXCLUDE_ASSET_ID
import jp.co.soramitsu.wallet.impl.presentation.balance.assetselector.AssetSelectFragment.Companion.KEY_FILTER_CHAIN_ID
import jp.co.soramitsu.wallet.impl.presentation.balance.assetselector.AssetSelectFragment.Companion.KEY_IS_FILTER_XCM_ASSETS
import jp.co.soramitsu.wallet.impl.presentation.balance.assetselector.AssetSelectFragment.Companion.KEY_PURPOSE
import jp.co.soramitsu.wallet.impl.presentation.balance.assetselector.AssetSelectFragment.Companion.KEY_SELECTED_ASSET_ID
import jp.co.soramitsu.wallet.impl.presentation.balance.assetselector.AssetSelectFragment.Companion.KEY_SELECTED_CHAIN_ID
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

/** The asset picker sheet lists the view model's assets, reports a pick and its closing. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h1600dp")
class AssetSelectFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val viewModel = mockk<AssetSelectViewModel>(relaxed = true)
    private var host: ActivityController<StubHiltActivity>? = null

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        every { viewModel.state } returns MutableStateFlow(
            AssetSelectScreenViewState(
                assets = listOf(
                    AssetItemState(
                        id = "dot", imageUrl = null, chainName = "Polkadot", symbol = "DOT", amount = "10",
                        fiatAmount = "$60", isSelected = true, chainId = "polkadot"
                    )
                )
            )
        )
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun sheetListsAssetsAndReportsThePick() {
        val fragment = AssetSelectFragment()
        host = hostSheet(fragment)

        compose.onNodeWithText(string(R.string.common_select_asset)).assertExists()
        compose.onNodeWithText("DOT", substring = true).performClick()
        settle()

        verify { viewModel.onAssetSelected(match { it.id == "dot" && it.chainId == "polkadot" }) }
        assertTrue((fragment.dialog as BottomSheetDialog).behavior.skipCollapsed)
    }

    @Test
    fun dismissingTheSheetTellsTheViewModel() {
        val fragment = AssetSelectFragment()
        host = hostSheet(fragment)

        fragment.dismiss()
        ShadowLooper.idleMainLooper()

        verify { viewModel.onDialogClose() }
    }

    @Test
    fun assetBundleNamesThePurpose() {
        val bundle = AssetSelectFragment.getBundle(assetId = "dot", selectedChainId = "polkadot", purpose = AssetSelectPurpose.RECEIVE)

        assertEquals("dot", bundle.getString(KEY_SELECTED_ASSET_ID))
        assertEquals("polkadot", bundle.getString(KEY_SELECTED_CHAIN_ID))
        assertEquals("RECEIVE", bundle.getString(KEY_PURPOSE))
    }

    @Test
    fun assetBundleDefaultsToASendPickerOnAnyNetwork() {
        val bundle = AssetSelectFragment.getBundle(assetId = "dot")

        assertNull(bundle.getString(KEY_SELECTED_CHAIN_ID))
        assertEquals(AssetSelectPurpose.SEND.name, bundle.getString(KEY_PURPOSE))
    }

    @Test
    fun chainFilterBundleKeepsOnlyTheNetworkAndPurpose() {
        val bundle = AssetSelectFragment.getBundleFilterByChain(chainId = "kusama", purpose = AssetSelectPurpose.SWAP_FROM)

        assertEquals("kusama", bundle.getString(KEY_FILTER_CHAIN_ID))
        assertEquals("SWAP_FROM", bundle.getString(KEY_PURPOSE))
        assertFalse(bundle.containsKey(KEY_SELECTED_ASSET_ID))
        assertEquals("SEND", AssetSelectFragment.getBundleFilterByChain("kusama").getString(KEY_PURPOSE))
    }

    @Test
    fun swapBundleExcludesTheOtherSide() {
        val bundle = AssetSelectFragment.getBundle(
            chainId = "kusama",
            assetId = "ksm",
            excludeAssetId = "usdt",
            purpose = AssetSelectPurpose.SWAP_TO
        )

        assertEquals("kusama", bundle.getString(KEY_FILTER_CHAIN_ID))
        assertEquals("ksm", bundle.getString(KEY_SELECTED_ASSET_ID))
        assertEquals("usdt", bundle.getString(KEY_EXCLUDE_ASSET_ID))
        assertEquals("SWAP_TO", bundle.getString(KEY_PURPOSE))
    }

    @Test
    fun xcmFilterBundleChoosesTheXcmOrSendPurpose() {
        val xcm = AssetSelectFragment.getBundle(chainId = "kusama", assetId = null, isFilterXcmAssets = true)
        val send = AssetSelectFragment.getBundle(chainId = "kusama", assetId = "ksm", isFilterXcmAssets = false)

        assertTrue(xcm.getBoolean(KEY_IS_FILTER_XCM_ASSETS))
        assertNull(xcm.getString(KEY_SELECTED_ASSET_ID))
        assertEquals("XCM", xcm.getString(KEY_PURPOSE))
        assertFalse(send.getBoolean(KEY_IS_FILTER_XCM_ASSETS))
        assertEquals("ksm", send.getString(KEY_SELECTED_ASSET_ID))
        assertEquals("SEND", send.getString(KEY_PURPOSE))
    }

    private fun hostSheet(fragment: AssetSelectFragment): ActivityController<StubHiltActivity> {
        val controller = HiltFragmentHost.host(fragment, mapOf(AssetSelectViewModel::class.java to viewModel))
        settle()
        return controller
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }
}
