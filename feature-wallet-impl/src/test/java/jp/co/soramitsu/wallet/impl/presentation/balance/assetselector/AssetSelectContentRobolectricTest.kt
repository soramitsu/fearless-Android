package jp.co.soramitsu.wallet.impl.presentation.balance.assetselector

import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.feature_wallet_impl.R
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
@Config(qualifiers = "w420dp-h1600dp")
class AssetSelectContentRobolectricTest(private val sunlit: Boolean) {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : AssetSelectContentInterface {
        override fun onAssetSelected(assetItemState: AssetItemState) { calls += "select:${assetItemState.id}" }
        override fun onSearchInput(input: String) { calls += "search:$input" }
        override fun onHiddenSectionToggle() { calls += "toggleHidden" }
    }

    @Before
    fun setUp() {
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
    fun listsAssetsAndTheExpandedHiddenSection() {
        render(
            AssetSelectScreenViewState(
                assets = listOf(
                    asset("dot", "DOT", "Polkadot", "10", "$60", selected = true),
                    asset("wnd", "WND", "Westend", null, "", testnet = true)
                ),
                hiddenAssets = listOf(asset("ksm", "KSM", "Kusama", "2", "$40")),
                isHiddenExpanded = true
            )
        )

        compose.onNodeWithText(string(R.string.common_select_asset)).assertIsDisplayed()
        compose.onNodeWithText("$60", substring = true).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.portfolio_balance_unavailable), substring = true).assertIsDisplayed()
        compose.onNodeWithText("${string(R.string.hidden_assets)} · 1").assertIsDisplayed()
        compose.onNodeWithTag("AssetSelect_hidden_section").performClick()
        compose.onNodeWithText("KSM", substring = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("do")
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("toggleHidden", "select:ksm", "search:do")))
    }

    @Test
    fun collapsedHiddenSectionKeepsItsAssetsOut() {
        render(AssetSelectScreenViewState(assets = emptyList(), hiddenAssets = listOf(asset("ksm", "KSM", "Kusama", "2", "$40"))))

        compose.onNodeWithTag("AssetSelect_hidden_section").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("$40", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun emptySendPickerSaysThereIsNothingToSend() {
        render(AssetSelectScreenViewState(assets = emptyList(), emptyMessage = AssetSelectEmptyMessage.NothingToSend))

        compose.onNodeWithText(string(R.string.asset_select_nothing_to_send)).assertIsDisplayed()
    }

    @Test
    fun emptySearchSaysNothingWasFound() {
        render(AssetSelectScreenViewState(assets = emptyList()))

        compose.onNodeWithText(string(R.string.common_search_network_and_assets_alert_description)).assertIsDisplayed()
    }

    @Test
    fun loadingDoesNotClaimThatNothingWasFound() {
        render(AssetSelectScreenViewState.default)

        assertEquals(0, compose.onAllNodes(hasText(string(R.string.common_search_network_and_assets_alert_description))).fetchSemanticsNodes().size)
    }

    @Test
    fun labelJoinsSymbolAndNetwork() {
        assertEquals("DOT · Polkadot", asset("dot", "DOT", "Polkadot", "1", "").label)
        assertEquals("DOT", asset("dot", "DOT", " ", "1", "").label)
    }

    private fun render(state: AssetSelectScreenViewState) {
        compose.setContent {
            FearlessAppTheme {
                AssetSelectContent(state = state, callback = callback)
            }
        }
        compose.waitForIdle()
    }

    private fun asset(
        id: String,
        symbol: String,
        chainName: String?,
        amount: String?,
        fiat: String,
        selected: Boolean = false,
        testnet: Boolean = false
    ) = AssetItemState(
        id = id,
        imageUrl = null,
        chainName = chainName,
        symbol = symbol,
        amount = amount,
        fiatAmount = fiat,
        isSelected = selected,
        chainId = "$id-chain",
        isTestnet = testnet
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "sunlit={0}")
        fun palettes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
