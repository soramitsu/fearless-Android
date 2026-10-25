package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.lang.reflect.Proxy
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.*
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.*
import jp.co.soramitsu.wallet.impl.presentation.balance.list.*
import jp.co.soramitsu.wallet.impl.presentation.manageassets.*
import jp.co.soramitsu.wallet.impl.presentation.send.confirm.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortfolioViewsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private val groups = ResolvedTokenGroups(listOf(ResolvedTokenGroup("usdt", "USDT", "Tether", 6,
        listOf(ResolvedTokenGroupMember("ethereum", "contract"), ResolvedTokenGroupMember("hub", "1984")))))
    private val assets = listOf(asset("ethereum", "contract", "Ethereum", "5"), asset("hub", "1984", "Polkadot Asset Hub", "7"))

    @Test fun portfolioHeaderShowsAndChangesBothLensesAtEachTextSize() {
        var scale by mutableStateOf(1f)
        var selected by mutableStateOf(PortfolioLens.ByToken)
        val callback = callbacks(WalletScreenInterface::class.java)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                FearlessAppTheme {
                    Box(Modifier.requiredWidth(320.dp).fillMaxHeight().background(Color.Black).testTag("fixture")) {
                        PortfolioList(AssetsLoadingState.Loaded(assets.map { it.state }, walletHasPositiveBalance = true,
                            walletId = 7, portfolioAssets = assets, availableAssets = assets,
                            portfolioItems = PortfolioListBuilder.build(assets, emptyList(), groups, PortfolioUiState(lens = selected), null, true),
                            portfolioUi = PortfolioUiState(lens = selected), tokenGroups = groups), true, callback, rememberLazyListState(), {
                            MultiToggleButton(MultiToggleButtonState(selected, PortfolioLens.entries), { selected = it }, stacked = scale > 1.3f)
                        }, {})
                    }
                }
            }
        }
        for (font in listOf(1f, 2f)) {
            compose.runOnIdle { scale = font; selected = PortfolioLens.ByToken }
            compose.waitForIdle()
            save("portfolio-header-lenses-${(font * 100).toInt()}")
            val token = compose.activity.getString(jp.co.soramitsu.feature_wallet_impl.R.string.portfolio_by_token)
            val network = compose.activity.getString(jp.co.soramitsu.feature_wallet_impl.R.string.portfolio_by_network)
            compose.onNodeWithText(token).assertIsDisplayed()
            compose.onNodeWithText(network).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(PortfolioLens.ByNetwork, selected) }
            compose.onNodeWithText(token).performClick()
            compose.runOnIdle { assertEquals(PortfolioLens.ByToken, selected) }
        }
    }

    @Test fun approvedGroupExpandsAndReceiveRequiresAnExactNetworkAtBothTextSizes() {
        var scale by mutableStateOf(1f)
        var ui by mutableStateOf(PortfolioUiState())
        var selected: List<Any?>? = null
        val callback = callbacks(WalletScreenInterface::class.java) { name, args ->
            when (name) {
                "onPortfolioGroupToggle" -> ui = ui.copy(expandedGroups = if (ui.expandedGroups.isEmpty()) setOf("usdt") else emptySet())
                "onPortfolioMemberAction" -> selected = args?.toList()
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                FearlessAppTheme {
                    Box(Modifier.requiredWidth(320.dp).fillMaxHeight().background(Color.Black).testTag("fixture")) {
                        key(scale) {
                            PortfolioList(AssetsLoadingState.Loaded(assets.map { it.state }, walletHasPositiveBalance = true,
                                walletId = 7, portfolioAssets = assets, availableAssets = assets,
                                portfolioItems = PortfolioListBuilder.build(assets, emptyList(), groups, ui, null, true),
                                portfolioUi = ui, tokenGroups = groups), true, callback, rememberLazyListState(), {}, {})
                        }
                    }
                }
            }
        }
        for (font in listOf(1f, 2f)) {
            compose.runOnIdle { scale = font; ui = PortfolioUiState(); selected = null }
            compose.waitForIdle()
            val total = compose.onNodeWithText("12 USDT", substring = true).assertIsDisplayed()
            val actions = total.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            assertTrue(actions.size >= 3)
            save("portfolio-collapsed-${(font * 100).toInt()}")
            total.performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Polkadot Asset Hub", substring = true))
            compose.onNodeWithText("Polkadot Asset Hub", substring = true).assertIsDisplayed()
            save("portfolio-expanded-${(font * 100).toInt()}")
            val receive = compose.activity.getString(jp.co.soramitsu.common.R.string.common_action_receive)
            val groupReceive = hasText(receive).and(hasAnyAncestor(hasTestTag("group:usdt")))
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(groupReceive)
            compose.onNode(groupReceive).performClick()
            compose.onNode(hasText("Ethereum").and(hasAnyAncestor(isDialog()))).assertIsDisplayed()
            compose.onNode(hasText("Polkadot Asset Hub").and(hasAnyAncestor(isDialog()))).assertIsDisplayed()
            saveWindow("portfolio-receive-network-${(font * 100).toInt()}")
            compose.onNode(hasText("Polkadot Asset Hub").and(hasAnyAncestor(isDialog()))).performClick()
            compose.runOnIdle {
                assertEquals(listOf(7L, "hub", "1984", ActionItemType.RECEIVE), selected)
            }
        }
    }

    @Test fun manageGroupShowsMixedStateAndHiddenFilterRemainsReachableAtDoubleTextSize() {
        var filter by mutableStateOf(ManageAssetFilter.All)
        var toggled: Boolean? = null
        val rows = assets.mapIndexed { index, asset -> ManageAssetItemState(asset.key, asset.state.chainAssetId, null,
            asset.state.assetChainName, "Tether", "USDT", requireNotNull(asset.total) { "This rendered fixture has a known total" }.toPlainString(), "$5", asset.state.chainId,
            index == 0, false, false, preference = if (index == 0) "shown" else "hidden") }
        val callback = callbacks(ManageAssetsContentInterface::class.java) { name, args ->
            when (name) {
                "onFilterChanged" -> filter = args?.first() as ManageAssetFilter
                "onGroupChecked" -> toggled = args?.get(1) as Boolean
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FearlessAppTheme {
                    Box(Modifier.requiredWidth(320.dp).fillMaxHeight().background(Color.Black).testTag("fixture")) {
                        ManageAssetsContent(ManageAssetsScreenViewState(
                            assets = mapOf("group:usdt" to rows.filter(filter::accepts)), allSectionAssets = mapOf("group:usdt" to rows),
                            selectedChainTitle = "All networks", visibilityFilter = filter), callback)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(isToggleable().and(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.ToggleableState, androidx.compose.ui.state.ToggleableState.Indeterminate)))
        compose.onNode(isToggleable().and(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState,
            androidx.compose.ui.state.ToggleableState.Indeterminate))).performClick()
        compose.runOnIdle { assertEquals(true, toggled) }
        val hidden = compose.activity.getString(jp.co.soramitsu.feature_wallet_impl.R.string.portfolio_filter_hidden)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("MultiToggleButton_$hidden"))
        compose.onNodeWithTag("MultiToggleButton_$hidden").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Polkadot Asset Hub"))
        compose.onNodeWithText("Polkadot Asset Hub").assertIsDisplayed()
        compose.onNodeWithText("Ethereum").assertDoesNotExist()
        val target = "manage_toggle:${assets[1].key}"
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(target))
        val toggle = compose.onNodeWithTag(target).assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("fixture").getUnclippedBoundsInRoot()
        assertTrue(toggle.top >= viewport.top && toggle.bottom <= viewport.bottom)
        save("portfolio-manage-hidden-controls-200")
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.onNodeWithTag(target).assertIsDisplayed()
        save("portfolio-manage-hidden-200")
    }

    @Test fun confirmListsCanonicalNetworkBeforeAddressesAtBothTextSizes() {
        var scale by mutableStateOf(1f)
        val state = ConfirmSendViewState(chainIconUrl = null,
            networkInfoItem = TitleValueViewState("Network", "Polkadot Asset Hub"),
            fromInfoItem = TitleValueViewState("From", "QA wallet"), toInfoItem = TitleValueViewState("To", "synthetic-recipient"),
            amountInfoItem = TitleValueViewState("Amount", "7 USDT"), feeInfoItem = TitleValueViewState("Fee", "0.01 DOT"),
            buttonState = ButtonViewState("Confirm", true))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                FearlessAppTheme {
                    Box(Modifier.requiredWidth(320.dp).fillMaxHeight().background(Color.Black).testTag("fixture")) {
                        ConfirmSendContent(state, callbacks(ConfirmSendScreenInterface::class.java))
                    }
                }
            }
        }
        for (font in listOf(1f, 2f)) {
            compose.runOnIdle { scale = font }
            compose.waitForIdle()
            compose.onNodeWithText("Network").performScrollTo().assertIsDisplayed()
            val network = compose.onNodeWithText("Network").getUnclippedBoundsInRoot()
            val from = compose.onNodeWithText("From").getUnclippedBoundsInRoot()
            assertTrue(network.top < from.top)
            val text = compose.onNodeWithText("Polkadot Asset Hub").assertIsDisplayed()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.all { result -> (0 until result.lineCount).none(result::isLineEllipsized) })
            save("portfolio-confirm-network-${(font * 100).toInt()}")
        }
    }

    private fun asset(chain: String, id: String, network: String, amount: String) = PortfolioAsset(
        AssetListItemViewState(null, "", "Tether", network, "USDT", null, null, amount, "$amount", emptyMap(), chain, id,
            true, false, isTestnet = false, ecosystemId = "test-fixture", priceTrust = "canonical", assetTotalBalance = amount,
            assetTotalBalanceFiat = "$amount"), BigDecimal(amount), BigDecimal(amount), "$", null)

    private fun <T> callbacks(type: Class<T>, onCall: (String, Array<out Any?>?) -> Unit = { _, _ -> }): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Portfolio fixture callbacks"
                else -> { onCall(method.name, args); null }
            }
        })!!

    private fun save(name: String) = write(name, compose.onNodeWithTag("fixture").captureToImage().asAndroidBitmap())
    private fun saveWindow(name: String) {
        compose.waitForIdle()
        // Compose idleness precedes the platform dialog-window animation.
        android.os.SystemClock.sleep(500)
        write(name, androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    }
    private fun write(name: String, bitmap: Bitmap) {
        val directory = compose.activity.getExternalFilesDir("wp567-evidence")!!.apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
