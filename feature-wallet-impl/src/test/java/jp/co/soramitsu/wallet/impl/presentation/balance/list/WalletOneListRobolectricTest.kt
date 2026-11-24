package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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

/** The one-list wallet home: search, network chips, how a row names its network, the no-match answer and the one-time note. */
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
    fun everyRowOfTheOneListNamesItsNetworkOnce() {
        render(OneListFixtures.home(searchQuery = "usdt", expandedGroups = setOf("usdt")))

        // In the row's own caption, and not on a line above the row as well.
        assertEquals(listOf("TETHER · MOONBEAM", "TETHER · POLKADOT ASSET HUB"), captions("usdt"))
        assertEquals("only the chip is Moonbeam by itself", 1, count("Moonbeam"))
        assertEquals("only the chip is Polkadot Asset Hub by itself", 1, count("Polkadot Asset Hub"))
    }

    @Test
    fun aNativeTokenNamedAfterItsNetworkNamesItOnce() {
        render(OneListFixtures.home())

        // Not "POLKADOT · POLKADOT" under a line saying Polkadot.
        assertEquals(listOf("POLKADOT"), captions("dot"))
        assertEquals(listOf("MOONBEAM"), captions("glmr"))
        assertEquals("only the chip is Polkadot by itself", 1, count("Polkadot"))
    }

    @Test
    fun underASelectedNetworkChipTheRowsLeaveTheNetworkToTheChip() {
        render(OneListFixtures.home(selectedNetwork = "moonbeam"))

        compose.revealChip(networkTag("moonbeam"))
        compose.onNodeWithTag(networkTag("moonbeam")).assertIsSelected()
        assertEquals(listOf("TETHER"), captions("usdt"))
        // GLMR is named Moonbeam itself.
        assertEquals(listOf("MOONBEAM"), captions("glmr"))
        assertEquals("only the chip is Moonbeam by itself", 1, count("Moonbeam"))
    }

    @Test
    fun withoutChipsTheRowsOfTheOnlyNetworkStillNameIt() {
        render(OneListFixtures.home(selectedNetwork = "moonbeam", networkFilterAvailable = false))

        assertEquals(listOf("TETHER · MOONBEAM"), captions("usdt"))
    }

    @Test
    fun underANetworkHeaderTheRowsLeaveTheNetworkToTheHeader() {
        render(OneListFixtures.networkSections())

        assertEquals(listOf("TETHER", "TETHER"), captions("usdt"))
        assertEquals(listOf("POLKADOT"), captions("dot"))
        assertEquals("only the header is Polkadot Asset Hub by itself", 1, count("Polkadot Asset Hub"))
        assertEquals(listOf("Tether, Moonbeam", "Tether, Polkadot Asset Hub"), spokenNames("usdt"))
    }

    @Test
    fun aScreenReaderHearsEachRowsTokenAndNetworkOnce() {
        // Every network: the caption shows both. A native token is not said twice.
        render(OneListFixtures.home())
        assertEquals(listOf("Polkadot"), spokenNames("dot"))
        assertEquals(1, timesSaid("AssetListItem_dot_Polkadot", "Polkadot"))
        assertEquals(1, timesSaid("AssetListItem_dot_Polkadot", "DOT"))
    }

    @Test
    fun aScreenReaderStillHearsTheNetworkOfARowUnderItsChip() {
        // The caption leaves the network to the chip; read aloud, the row names it all the same.
        render(OneListFixtures.home(selectedNetwork = "moonbeam"))

        assertEquals(listOf("Tether, Moonbeam"), spokenNames("usdt"))
        assertEquals(1, timesSaid("AssetListItem_usdt_Tether", "Moonbeam"))
        assertEquals(1, timesSaid("AssetListItem_usdt_Tether", "Tether"))
    }

    @Test
    fun aRestingRowKeepsNoActionsBesideItUntilItIsSwiped() {
        render(OneListFixtures.home())
        val receive = hasText(string(R.string.common_action_receive)) and hasAnyAncestor(hasTestTag("SwipeBox"))

        // Nothing to be drawn in the gutter beside the row, nothing unseen to tap there.
        assertEquals(0, compose.onAllNodes(receive).fetchSemanticsNodes().size)

        compose.onAllNodes(hasTestTag("SwipeBox"))[0].performTouchInput { swipeRight() }
        repeat(3) { compose.waitForIdle() }
        ComposeFixtures.passDoubleTapGuard()
        compose.onNode(receive).assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertEquals(listOf("action:RECEIVE:dot"), callback.calls)
    }

    @Test
    fun aRowSwipedLeftOffersHide() {
        render(OneListFixtures.home())
        val hide = hasText(string(R.string.common_action_hide)) and hasAnyAncestor(hasTestTag("SwipeBox"))
        assertEquals(0, compose.onAllNodes(hide).fetchSemanticsNodes().size)

        compose.onAllNodes(hasTestTag("SwipeBox"))[0].performTouchInput { swipeLeft() }
        repeat(3) { compose.waitForIdle() }
        ComposeFixtures.passDoubleTapGuard()
        compose.onNode(hide).assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertEquals(listOf("action:HIDE:dot"), callback.calls)
    }

    @Test
    fun aRowsSwipeActionsAreAlsoItsAccessibilityActions() {
        render(OneListFixtures.home())

        val actions = compose.onNodeWithTag("AssetListItem_dot_Polkadot").fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        val labels = listOf(R.string.common_action_send, R.string.common_action_receive, R.string.common_action_hide).map { string(it) }
        assertEquals(labels, actions.map { it.label })
        compose.runOnIdle { actions.forEach { it.action() } }

        assertEquals(listOf("action:SEND:dot", "action:RECEIVE:dot", "action:HIDE:dot"), callback.calls)
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

    /** The caption of each row of a token, top to bottom. */
    private fun captions(symbol: String): List<String> =
        captionNodes(symbol).map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }

    /** What a screen reader says in place of each of those captions. */
    private fun spokenNames(symbol: String): List<String> =
        captionNodes(symbol).map { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString() }

    private fun captionNodes(symbol: String) =
        compose.onAllNodes(hasTestTag("AssetListItem_${symbol}_chain_name"), useUnmergedTree = true).fetchSemanticsNodes()

    /** Nodes whose own text is exactly [text]: a chip or a header, never a row that merely mentions it. */
    private fun count(text: String): Int = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    /** How often a screen reader says [word] when it reads the row: a part's description when it has one, else its text. */
    private fun timesSaid(rowTag: String, word: String): Int {
        fun SemanticsNode.spoken(): List<String> {
            val own = config.getOrNull(SemanticsProperties.ContentDescription)?.takeIf { it.isNotEmpty() }
                ?: config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
            return own + children.flatMap { it.spoken() }
        }
        val said = compose.onNodeWithTag(rowTag, useUnmergedTree = true).fetchSemanticsNode().spoken().joinToString(" | ")
        return Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE).findAll(said).count()
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
