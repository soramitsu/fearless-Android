package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The one-list wallet home: search, network chips, the no-match answer and the one-time note. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h4000dp")
class WalletOneListRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val callback = RecordingWalletCallback()

    @After
    fun tearDown() {
        unmockkObject(FwRestyle)
    }

    @Test
    fun typingInTheSearchReportsTheQueryAndTheFieldShowsTheCurrentOne() {
        render(OneListFixtures.home(searchQuery = "usd"))

        compose.onNode(hasText("usd") and hasAnyAncestor(hasTestTag(SEARCH_TAG))).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SEARCH_TAG))).performTextInput("t")
        compose.waitForIdle()

        assertTrue(callback.calls.toString(), callback.calls.any { it.startsWith("search:") && it.contains("t") })
    }

    @Test
    fun chipsSelectANetworkEveryNetworkOrTheFullList() {
        render(OneListFixtures.home())

        compose.onNodeWithTag(ALL_TAG).assertIsSelected()
        compose.revealChip(networkTag("moonbeam"))
        compose.onNodeWithTag(networkTag("moonbeam")).assertIsNotSelected().performClick()
        compose.revealChip(ALL_TAG)
        compose.onNodeWithTag(ALL_TAG).performClick()
        compose.revealChip(MORE_TAG)
        compose.onNodeWithTag(MORE_TAG).performClick()
        compose.waitForIdle()

        assertEquals(listOf("networkSelect:moonbeam", "networkSelect:null", "moreNetworks"), callback.calls)
        assertEquals(0, compose.onAllNodes(hasTestTag(GROUP_TAG)).fetchSemanticsNodes().size)
    }

    @Test
    fun theSelectedNetworkChipIsSelectedAndPickingItAgainAsksForEveryNetwork() {
        render(OneListFixtures.home(selectedNetwork = "moonbeam"))

        compose.onNodeWithTag(ALL_TAG).assertIsNotSelected()
        compose.revealChip(networkTag("moonbeam"))
        compose.onNodeWithTag(networkTag("moonbeam")).assertIsSelected().performClick()
        compose.waitForIdle()

        assertEquals(listOf("networkSelect:moonbeam"), callback.calls)
    }

    @Test
    fun aGroupKeepsItsChipWhichOpensTheFullNetworkList() {
        render(OneListFixtures.home(networkGroup = ChainSelectorViewStateWithFilters.Filter.Popular))

        compose.onNodeWithTag(ALL_TAG).assertIsNotSelected()
        compose.onNode(hasTestTag(GROUP_TAG) and hasText(string(R.string.network_management_popular))).assertExists()
        compose.onNodeWithTag(GROUP_TAG).assertIsSelected().performClick()
        compose.waitForIdle()

        assertEquals(listOf("moreNetworks"), callback.calls)
    }

    @Test
    fun aSelectedNetworkChipOutOfSightIsScrolledIntoTheRow() {
        render(OneListFixtures.home(selectedNetwork = "assethub"))

        val chip = compose.onNodeWithTag(networkTag("assethub")).assertIsSelected().getUnclippedBoundsInRoot()
        val row = compose.onNodeWithTag(FILTERS_TAG).getUnclippedBoundsInRoot()
        assertTrue("chip $chip, row $row", chip.left >= 0.dp && chip.right <= 420.dp)
    }

    @Test
    fun aWalletHeldToOneNetworkGetsTheSearchButNoChips() {
        render(OneListFixtures.home(networkFilterAvailable = false))

        compose.onNodeWithTag(SEARCH_TAG).assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasTestTag(FILTERS_TAG)).fetchSemanticsNodes().size)
    }

    @Test
    fun aSearchThatFindsNothingOnOneNetworkOffersEveryNetwork() {
        render(OneListFixtures.noMatch(selectedNetwork = "moonbeam"))

        compose.onNodeWithTag(SEARCH_EMPTY_TAG).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_search_network_and_assets_alert_description)).assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_ALL_TAG).performClick()
        compose.waitForIdle()

        assertEquals(listOf("networkSelect:null"), callback.calls)
    }

    @Test
    fun aSearchThatFindsNothingOnEveryNetworkHasNothingMoreToOffer() {
        render(OneListFixtures.noMatch(selectedNetwork = null))

        compose.onNodeWithTag(SEARCH_EMPTY_TAG).assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasTestTag(SEARCH_ALL_TAG)).fetchSemanticsNodes().size)
        assertEquals("not the empty-wallet card", 0, compose.onAllNodes(hasText(string(R.string.wallet_all_assets_hidden))).fetchSemanticsNodes().size)
    }

    @Test
    fun theOneListNoteIsShownUntilItIsClosed() {
        render(OneListFixtures.home(showOneListNotice = true))

        compose.onNodeWithText(string(R.string.portfolio_one_list_notice)).assertIsDisplayed()
        compose.onNode(hasText(string(R.string.common_got_it)) and hasAnyAncestor(hasTestTag("portfolio_one_list_notice"))).performClick()
        compose.waitForIdle()

        assertEquals(listOf("oneListNotice"), callback.calls)
    }

    @Test
    fun everyRowOfTheOneListNamesItsNetwork() {
        render(OneListFixtures.home(searchQuery = "usdt", expandedGroups = setOf("usdt")))

        // A chip and the row each name the network.
        assertTrue(compose.onAllNodes(hasText("Moonbeam")).fetchSemanticsNodes().size >= 2)
        assertTrue(compose.onAllNodes(hasText("Polkadot Asset Hub")).fetchSemanticsNodes().size >= 2)
    }

    @Test
    fun legacyPaletteChipsWorkTheSameWay() {
        mockkObject(FwRestyle)
        every { FwRestyle.isEnabled(any()) } returns false
        render(OneListFixtures.home(networkGroup = ChainSelectorViewStateWithFilters.Filter.Favorite))

        compose.revealChip(networkTag("assethub"))
        compose.onNodeWithTag(networkTag("assethub")).performClick()
        compose.revealChip(GROUP_TAG)
        compose.onNodeWithTag(GROUP_TAG).performClick()
        compose.revealChip(MORE_TAG)
        compose.onNodeWithTag(MORE_TAG).performClick()
        compose.waitForIdle()

        assertEquals(listOf("networkSelect:assethub", "moreNetworks", "moreNetworks"), callback.calls)
    }

    @Test
    fun theFooterPartsFollowOneAnotherInsteadOfOverlapping() {
        val home = OneListFixtures.home()
        val loaded = (home.assetsState as WalletAssetsState.Assets).assets as AssetsLoadingState.Loaded
        render(OneListFixtures.state(loaded.with()).copy(hasTonAccounts = false, hasSubOrEvmAccounts = false))

        val manage = compose.onNodeWithText(string(R.string.wallet_manage_assets)).getUnclippedBoundsInRoot()
        val evm = compose.onNodeWithText(string(jp.co.soramitsu.common.R.string.banner_addwallet_regular_title)).getUnclippedBoundsInRoot()
        val ton = compose.onNodeWithText(string(jp.co.soramitsu.common.R.string.banner_addwallet_ton_title)).getUnclippedBoundsInRoot()
        assertTrue("Manage assets $manage, banner $evm", manage.bottom <= evm.top)
        assertTrue("banners $evm, $ton", evm.bottom <= ton.top)
    }

    private fun render(state: WalletState) {
        compose.setContent {
            FearlessAppTheme {
                WalletScreenWithRefresh(data = state, callback = callback)
            }
        }
        compose.waitForIdle()
        ComposeFixtures.passDoubleTapGuard()
    }
}
