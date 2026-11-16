package jp.co.soramitsu.wallet.impl.presentation.manageassets

import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h2400dp")
class ManageAssetsContentRobolectricTest(private val sunlit: Boolean) {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : ManageAssetsContentInterface {
        override fun onSearchInput(input: String) { calls += "search:$input" }
        override fun onChecked(assetItemState: ManageAssetItemState, checked: Boolean) { calls += "check:${assetItemState.id}:$checked" }
        override fun onItemClicked(assetItemState: ManageAssetItemState) { calls += "item:${assetItemState.id}" }
        override fun onEditClicked(assetItemState: ManageAssetItemState) { calls += "edit:${assetItemState.id}" }
        override fun onDoneClicked() { calls += "done" }
        override fun onSelectedChainClicked() { calls += "chain" }
        override fun onFilterChanged(filter: ManageAssetFilter) { calls += "filter:$filter" }
        override fun onSectionToggle(id: String) { calls += "toggle:$id" }
        override fun onGroupChecked(section: String, checked: Boolean) { calls += "group:$section:$checked" }
        override fun onShowTestnetsChanged(show: Boolean) { calls += "testnets:$show" }
    }

    @Before
    fun setUp() {
        ComposeFixtures.passDoubleTapGuard()
        if (!sunlit) {
            mockkObject(FwRestyle)
            every { FwRestyle.isEnabled(any()) } returns false
        }
    }

    @After
    fun tearDown() {
        unmockkObject(FwRestyle)
    }

    @Test
    fun sectionsGroupsAndRowsReportEveryControl() {
        val ksm = item("1", "KSM", "Kusama", checked = true, preference = "shown")
        val movr = item("2", "MOVR", "Moonriver", checked = false, preference = "hidden", hiddenNote = "Hidden in previous version")
        val wnd = item("3", "WND", "Westend", checked = true, testnet = true)
        render(
            ManageAssetsScreenViewState(
                assets = mapOf(
                    "plain" to listOf(ksm),
                    "group:usdt" to listOf(movr, wnd),
                    "network:westend" to listOf(wnd)
                ),
                allSectionAssets = mapOf("group:usdt" to listOf(movr, wnd)),
                selectedChainTitle = "All networks",
                collapsedSections = setOf("network:westend")
            )
        )

        compose.onNodeWithText("All networks").performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.common_done)).performClick()
        compose.onNodeWithText(string(R.string.portfolio_manage_group_count, 1, 2)).performClick()
        compose.onNodeWithTag("ManageAssetItem_hidden_note_2", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("manage_toggle:key-1", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("MultiToggleButton_${string(R.string.portfolio_filter_hidden)}").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("ks")
        compose.onAllNodes(isToggleable()).fetchSemanticsNodes().let { nodes ->
            assertTrue(nodes.any { it.config.getOrElseNullable(SemanticsProperties.ToggleableState) { null } == ToggleableState.Indeterminate })
        }
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("chain", "done", "toggle:group:usdt", "check:1:false", "filter:Hidden", "search:ks")))
        assertTrue(compose.onAllNodes(hasText(string(R.string.label_testnet)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun groupCheckboxChecksEveryMember() {
        val a = item("1", "USDT", "Moonbeam", checked = false)
        val b = item("2", "USDT", "Asset Hub", checked = false)
        render(ManageAssetsScreenViewState(assets = mapOf("group:usdt" to listOf(a, b))))

        val untagged = compose.onAllNodes(isToggleable() and hasNoTag())
        // The testnet switch comes first; the group's tri-state box is the last untagged toggle.
        untagged[untagged.fetchSemanticsNodes().size - 1].performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.contains("group:group:usdt:true"))
    }

    @Test
    fun showTestnetsSwitchReportsItsNewValue() {
        render(ManageAssetsScreenViewState(assets = emptyMap(), showTestnets = false))

        compose.onNodeWithTag("ManageAssets_show_testnets").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.portfolio_show_testnets)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_empty_search)).assertIsDisplayed()
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.contains("testnets:true"))
    }

    @Test
    fun hiddenFilterWithoutAssetsSaysNothingIsHidden() {
        render(ManageAssetsScreenViewState(assets = emptyMap(), visibilityFilter = ManageAssetFilter.Hidden))

        compose.onNodeWithText(string(R.string.portfolio_no_hidden)).assertIsDisplayed()
    }

    @Test
    fun loadingShowsTheControlsOnly() {
        render(ManageAssetsScreenViewState.default)

        compose.onNodeWithText(string(R.string.wallet_manage_assets)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("manage_toggle:key-1").fetchSemanticsNodes().size)
    }

    @Test
    fun filtersAcceptMatchingAssets() {
        val shown = item("1", "KSM", "Kusama", checked = true, preference = "shown")
        val hidden = item("2", "MOVR", "Moonriver", checked = false, preference = "hidden")

        assertTrue(ManageAssetFilter.All.accepts(hidden))
        assertTrue(ManageAssetFilter.Shown.accepts(shown))
        assertEquals(false, ManageAssetFilter.Shown.accepts(hidden))
        assertTrue(ManageAssetFilter.Hidden.accepts(hidden))
        assertEquals("kusama-chain:1", shown.listKey)
    }

    private fun hasNoTag() = androidx.compose.ui.test.SemanticsMatcher("has no test tag") {
        !it.config.contains(SemanticsProperties.TestTag)
    }

    private fun render(state: ManageAssetsScreenViewState) {
        compose.setContent {
            FearlessAppTheme {
                ManageAssetsContent(state = state, callback = callback)
            }
        }
        compose.waitForIdle()
    }

    private fun item(
        id: String,
        symbol: String,
        chainName: String,
        checked: Boolean,
        preference: String = "auto",
        hiddenNote: String? = null,
        testnet: Boolean = false
    ) = ManageAssetItemState(
        canonicalAssetKey = "key-$id",
        id = id,
        imageUrl = null,
        chainName = chainName,
        assetName = "$symbol asset",
        symbol = symbol,
        amount = "1.5",
        fiatAmount = "$3",
        chainId = "${chainName.lowercase()}-chain",
        isChecked = checked,
        showEdit = true,
        isZeroAmount = false,
        hiddenNote = hiddenNote,
        preference = preference,
        isTestnet = testnet
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "sunlit={0}")
        fun palettes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
