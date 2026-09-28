package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Bitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.lang.reflect.Proxy
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeScreen
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeScreenInterface
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeState
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens.SwapTokensCallbacks
import jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens.SwapTokensContent
import jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens.SwapTokensContentViewState
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.receive.ReceiveScreen
import jp.co.soramitsu.wallet.impl.presentation.receive.ReceiveScreenInterface
import jp.co.soramitsu.wallet.impl.presentation.receive.ReceiveScreenViewState
import jp.co.soramitsu.wallet.impl.presentation.receive.model.ReceiveToggleType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletUxScreensInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    @Test
    fun largeTextWalletScrollsBackupBalanceAndFooterWithinTheReservedViewport() {
        var fixtureState by mutableStateOf("loaded")
        val assets = listOf(jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState(
            null, "", "Ceres", "SORA test", "CERES", null, null, "0", null,
            emptyMap(), "sora-test", "ceres", true, false, isTestnet = true))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FearlessAppTheme {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(310.dp).background(Color.Black).testTag("fixture")) {
                        jp.co.soramitsu.wallet.impl.presentation.balance.list.WalletScreen(
                            jp.co.soramitsu.wallet.impl.presentation.balance.list.WalletState.default.copy(
                                assetsState = when (fixtureState) {
                                    "issue" -> jp.co.soramitsu.wallet.impl.presentation.balance.list.WalletAssetsState.NetworkIssue(
                                        "sora-test", jp.co.soramitsu.common.domain.model.NetworkIssueType.values().first(), false)
                                    else -> jp.co.soramitsu.wallet.impl.presentation.balance.list.WalletAssetsState.Assets(
                                        if (fixtureState == "loading") jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState.Loading()
                                        else jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState.Loaded(
                                            if (fixtureState == "empty") emptyList() else assets), false)
                                },
                                balance = jp.co.soramitsu.common.compose.component.AssetBalanceViewState(
                                    "$0", "synthetic-public-address", true,
                                    jp.co.soramitsu.common.compose.component.ChangeBalanceViewState("+0%", "$0")),
                                isBackedUp = false, hasTonAccounts = true, hasSubOrEvmAccounts = true,
                                showCurrenciesOrNftSelector = true),
                            callbacks(jp.co.soramitsu.wallet.impl.presentation.balance.list.WalletScreenInterface::class.java))
                    }
                }
            }
        }
        compose.waitForIdle()
        val backup = compose.activity.getString(jp.co.soramitsu.common.R.string.ux_backup_status)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(backup))
        assertFullyVisible(backup)
        save("wallet-backup-320dp-font200")
        val manage = compose.activity.getString(jp.co.soramitsu.common.R.string.wallet_manage_assets)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(manage))
        assertFullyVisible(manage)
        compose.onNodeWithText(manage).performClick()
        save("wallet-footer-320dp-font200")
        compose.runOnIdle { fixtureState = "empty" }
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(backup))
        assertFullyVisible(backup)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(manage))
        assertFullyVisible(manage)
        save("wallet-empty-footer-320dp-font200")
        compose.runOnIdle { fixtureState = "loading" }
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(backup))
        assertFullyVisible(backup)
        save("wallet-loading-backup-320dp-font200")
        compose.runOnIdle { fixtureState = "issue" }
        compose.waitForIdle()
        val retry = compose.activity.getString(jp.co.soramitsu.common.R.string.common_try_again)
        assertFullyVisible(retry)
        compose.onNodeWithText(retry).performClick()
        save("wallet-retry-320dp-font200")
    }

    @Test
    fun walletHeaderAndNetworkSectionFitAtDoubleTextSize() {
        val networkName = "SORA test"
        val scoreLabel = compose.activity.getString(jp.co.soramitsu.common.R.string.account_stats_wallet_option_title)
        var scoreClicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FearlessAppTheme {
                    Column(Modifier.requiredWidth(320.dp).fillMaxHeight().background(Color.Black).testTag("fixture")) {
                        jp.co.soramitsu.common.compose.component.MainToolbar(
                            jp.co.soramitsu.common.compose.component.MainToolbarViewStateWithFilters(
                                "QA wallet",
                                jp.co.soramitsu.common.compose.component.ToolbarHomeIconState.Wallet(
                                    android.graphics.drawable.ColorDrawable(android.graphics.Color.GRAY), -2),
                                jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters(
                                    selectedChainName = networkName, allowChainSelection = true)),
                            onChangeChainClick = {}, onScoreClick = { scoreClicks++ },
                            menuItems = listOf(
                                jp.co.soramitsu.common.compose.component.MenuIconItem(jp.co.soramitsu.common.R.drawable.ic_scan, {}),
                                jp.co.soramitsu.common.compose.component.MenuIconItem(jp.co.soramitsu.common.R.drawable.ic_search, {})))
                        jp.co.soramitsu.wallet.impl.presentation.common.AssetsList(
                            jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState.Loaded(listOf(
                                jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState(
                                    null, "", "Ceres", "SORA test network", "CERES", null, null, "0", null,
                                    emptyMap(), "sora-test", "ceres", true, false, isTestnet = true,
                                    ecosystemId = "substrate", networkAccountLabel = "QA wallet",
                                    networkFiatSubtotal = "$1,234.56",
                                    networkLastSuccessMillis = System.currentTimeMillis(),
                                    networkScanCoverage = "CatalogOnly"))),
                            false, callbacks(jp.co.soramitsu.wallet.impl.presentation.common.AssetsListInterface::class.java))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("SUBSTRATE · QA wallet").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.common.R.string.portfolio_sync_catalog_only)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.common.R.string.portfolio_balances_not_loaded)).assertDoesNotExist()
        for (text in listOf(networkName, "SORA test network", "$1,234.56")) {
            val layouts = mutableListOf<TextLayoutResult>()
            val node = compose.onNodeWithText(text).assertIsDisplayed()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach {
                assertTrue("$text must not clip vertically", it.multiParagraph.height <= it.size.height + 1)
                assertTrue("$text must not collapse into character-wide lines", it.lineCount <= 2)
            }
        }
        val score = compose.onNodeWithContentDescription(scoreLabel).assertIsDisplayed()
        val bounds = score.getUnclippedBoundsInRoot()
        assertTrue("Score is a full touch target", bounds.right - bounds.left >= 48.dp && bounds.bottom - bounds.top >= 48.dp)
        score.performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, scoreClicks) }
        save("wallet-header-network-320dp-font200")
    }

    @Test
    fun longWalletNameKeepsNamedOptionsAndLargeTextTabsFit() {
        var optionsClicks = 0
        var selection by mutableStateOf(jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType.Currencies)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FearlessAppTheme {
                    Column(Modifier.requiredWidth(320.dp).fillMaxHeight().background(Color.Black).testTag("fixture")) {
                        jp.co.soramitsu.common.compose.component.WalletItem(
                            jp.co.soramitsu.common.compose.component.WalletItemViewState(
                                1, balance = "$0", title = "RestoreFixture",
                                walletIcon = android.graphics.drawable.ColorDrawable(android.graphics.Color.GRAY), isSelected = false),
                            onOptionsClick = { optionsClicks++ })
                        jp.co.soramitsu.common.compose.component.MultiToggleButton(
                            MultiToggleButtonState(selection, jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType.values().toList()),
                            onToggleChange = { selection = it }, stacked = true)
                    }
                }
            }
        }
        compose.waitForIdle()
        val options = compose.onNodeWithContentDescription(compose.activity.getString(jp.co.soramitsu.common.R.string.ux_more)).assertIsDisplayed()
        val bounds = options.getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("fixture").getUnclippedBoundsInRoot()
        assertTrue("Long wallet name must leave the full options target in view", bounds.left >= viewport.left && bounds.right <= viewport.right)
        options.performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, optionsClicks) }
        compose.onNodeWithText("RestoreFixture").assertExists()
        save("wallet-long-name-tabs-320dp-font200")
        for (item in jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType.values()) {
            val label = compose.activity.getString(item.titleResId)
            val node = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("$label must remain a whole readable label: ${layouts.map { listOf(it.lineCount, it.multiParagraph.height, it.size) }}",
                layouts.isNotEmpty() && layouts.all { it.lineCount == 1 && it.multiParagraph.height <= it.size.height + 1 &&
                    it.getLineRight(0) - it.getLineLeft(0) <= it.size.width + 1 })
            node.performClick()
            compose.runOnIdle { org.junit.Assert.assertEquals(item, selection) }
        }
        save("wallet-long-name-tabs-320dp-font200")
    }

    @Test
    fun walletOptionsTitleAndWrappedActionsFitAtDoubleTextSize() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FearlessAppTheme {
                    Box(Modifier.requiredWidth(320.dp).fillMaxHeight().background(Color.Black).testTag("fixture")) {
                        jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet.OptionsWalletContent(
                            jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet.OptionsWalletScreenViewState(
                                isSelected = false, showScoreButton = true, showDetailsButton = true),
                            callbacks(jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet.OptionsWalletCallback::class.java))
                    }
                }
            }
        }
        compose.waitForIdle()
        val title = compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.common.R.string.common_title_wallet_option))
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val close = compose.onNodeWithContentDescription(compose.activity.getString(jp.co.soramitsu.common.R.string.ux_close))
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Title and Close must occupy separate space", title.right <= close.left)
        assertTrue(close.right - close.left >= 48.dp && close.bottom - close.top >= 48.dp)
        save("wallet-options-320dp-font200-top")
        for (id in listOf(jp.co.soramitsu.common.R.string.export_wallet, jp.co.soramitsu.common.R.string.common_details_wallet,
            jp.co.soramitsu.common.R.string.change_wallet_name, jp.co.soramitsu.common.R.string.account_stats_wallet_option_title,
            jp.co.soramitsu.common.R.string.common_delete_wallet)) {
            val label = compose.activity.getString(id)
            assertFullyVisible(label)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("$label must retain every line", layouts.isNotEmpty() && layouts.all {
                it.multiParagraph.height <= it.size.height + 1 })
        }
        save("wallet-options-320dp-font200-actions")
    }

    @Test
    fun receiveRequestRendersInsideLegacyWalletThemeAtDoubleTextSize() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        compose.setContent {
            jp.co.soramitsu.common.compose.theme.FearlessTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Box(Modifier.requiredWidth(320.dp).fillMaxHeight().testTag("fixture")) {
                        ReceiveScreen(LoadingState.Loaded(ReceiveScreenViewState(
                            bitmap, "XOR", WalletAccount("synthetic-public-address", "QA wallet"),
                            MultiToggleButtonState(ReceiveToggleType.Request, ReceiveToggleType.values().toList()),
                            AmountInputViewState(totalBalance = "Available: 0 XOR", fiatAmount = null,
                                tokenAmount = BigDecimal.ONE, title = "Amount", tokenName = "XOR"),
                            true, "SORA"
                        )), callbacks(ReceiveScreenInterface::class.java))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("XOR").assertExists()
        compose.onNodeWithText("synthetic-public-address").performScrollTo().assertIsDisplayed()
        assertFullyVisible(compose.activity.getString(jp.co.soramitsu.feature_wallet_impl.R.string.common_copy_address))
        assertFullyVisible(compose.activity.getString(jp.co.soramitsu.feature_wallet_impl.R.string.ux_share_address))
    }

    @Test
    fun networkDiscoveryBannersRenderInsideLegacyWalletTheme() {
        compose.setContent {
            jp.co.soramitsu.common.compose.theme.FearlessTheme {
                androidx.compose.foundation.layout.Column {
                    jp.co.soramitsu.common.compose.component.BannerJoinSubstrateEvm({}, {})
                    jp.co.soramitsu.common.compose.component.BannerJoinTon({}, {})
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.common.R.string.banner_addwallet_ton_title)).assertExists()
        compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.common.R.string.banner_addwallet_regular_title)).assertExists()
    }

    @Test
    fun productionScreensRemainReadableAtNarrowWidthAndDoubleTextSize() {
        var screen by mutableStateOf("welcome")
        var fontScale by mutableStateOf(1f)
        val address = "0x1234567890abcdef1234567890abcdef12345678"
        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(address, com.google.zxing.BarcodeFormat.QR_CODE, 200, 200)
        val qrBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        for (y in 0 until 200) for (x in 0 until 200) {
            qrBitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        val networkTitle = compose.activity.getString(jp.co.soramitsu.feature_onboarding_impl.R.string.ux_network_ton)
        val networkAssets = compose.activity.getString(jp.co.soramitsu.feature_onboarding_impl.R.string.ux_asset_ton)
        var networkClicks = 0
        val welcome = callbacks(WelcomeScreenInterface::class.java)
        val receive = callbacks(ReceiveScreenInterface::class.java)
        val swap = callbacks(SwapTokensCallbacks::class.java)
        val amount = AmountInputViewState(totalBalance = "Available: 100 XOR", fiatAmount = "$10.00", tokenAmount = BigDecimal.TEN, title = "You pay", tokenName = "XOR")
        val details = SwapDetailsViewState("xor", "val", "XOR", "VAL", null, null, "10 XOR", "20 VAL", "19.90 VAL", "$9.95", "2", "0.5", "Minimum received", "XOR → VAL")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                FearlessAppTheme {
                    Box(Modifier.requiredWidth(320.dp).fillMaxHeight().background(Color.Black).testTag("fixture")) {
                        key(screen, fontScale) {
                            when (screen) {
                                "welcome" -> NavHost(rememberNavController(), startDestination = "WelcomeScreen") {
                                    WelcomeScreen(MutableStateFlow(WelcomeState()), false, welcome)
                                }
                                "networks" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                                    jp.co.soramitsu.onboarding.impl.welcome.EcosystemCard(
                                        networkTitle, networkAssets,
                                        jp.co.soramitsu.feature_onboarding_impl.R.drawable.background_banner_ton,
                                        onClick = { networkClicks++ })
                                    jp.co.soramitsu.onboarding.impl.welcome.TermsAndConditions({}, {})
                                }
                                "receive" -> ReceiveScreen(LoadingState.Loaded(ReceiveScreenViewState(
                                    qrBitmap, "USDT", WalletAccount(address, "My wallet"),
                                    MultiToggleButtonState(ReceiveToggleType.Receive, ReceiveToggleType.values().toList()), amount, false, "Ethereum"
                                )), receive)
                                "swap" -> SwapTokensContent(SwapTokensContentViewState(
                                    amount, amount.copy(title = "You receive", tokenName = "VAL", tokenAmount = BigDecimal(20), totalBalance = "Balance: 0 VAL"),
                                    Market.SMART, details, false, LoadingState.Loaded(SwapDetailsViewState.NetworkFee("XOR", "0.01 XOR", "$0.01")),
                                    true, true, false
                                ), swap)
                            }
                        }
                    }
                }
            }
        }
        for (scale in listOf(1f, 2f)) {
            for (name in listOf("welcome", "networks", "receive", "swap")) {
                compose.runOnIdle { fontScale = scale; screen = name }
                compose.waitForIdle()
                save("$name-320dp-font${(scale * 100).toInt()}-top")
                when (name) {
                    "welcome" -> {
                        compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.feature_onboarding_impl.R.string.username_setup_title_2_0)).assertIsDisplayed()
                        compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.feature_onboarding_impl.R.string.onboarding_restore_wallet)).assertIsDisplayed()
                        for (id in listOf(jp.co.soramitsu.feature_onboarding_impl.R.string.username_setup_title_2_0, jp.co.soramitsu.feature_onboarding_impl.R.string.onboarding_restore_wallet)) {
                            val layouts = mutableListOf<TextLayoutResult>()
                            compose.onNodeWithText(compose.activity.getString(id)).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                            layouts.forEach {
                                assertTrue("scale=$scale text=${it.layoutInput.text.text} layout=${it.size} paragraph=${it.multiParagraph.height}", it.multiParagraph.height <= it.size.height + 1)
                                for (line in 0 until it.lineCount) {
                                    assertTrue("scale=$scale text=${it.layoutInput.text.text} line=$line right=${it.getLineRight(line)} size=${it.size}", it.getLineRight(line) - it.getLineLeft(line) <= it.size.width + 1)
                                }
                            }
                        }
                        compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.feature_onboarding_impl.R.string.onboarding_privacy_policy)).performScrollTo().assertIsDisplayed()
                        save("$name-320dp-font${(scale * 100).toInt()}-consent")
                    }
                    "networks" -> {
                        org.junit.Assert.assertEquals(
                            compose.onNodeWithText(networkTitle).fetchSemanticsNode().id,
                            compose.onNodeWithText(networkAssets).fetchSemanticsNode().id)
                        compose.onNodeWithText(networkTitle).performScrollTo().performClick()
                        compose.runOnIdle { assertTrue(networkClicks > 0) }
                        for (id in listOf(jp.co.soramitsu.feature_onboarding_impl.R.string.onboarding_terms_and_conditions_2,
                            jp.co.soramitsu.feature_onboarding_impl.R.string.onboarding_privacy_policy)) {
                            val text = compose.activity.getString(id)
                            assertFullyVisible(text)
                            val layouts = mutableListOf<TextLayoutResult>()
                            compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                            layouts.forEach { assertTrue("Consent must wrap within the viewport", it.lineCount <= 2) }
                            val bounds = compose.onNodeWithText(text).getUnclippedBoundsInRoot()
                            assertTrue(bounds.bottom - bounds.top >= 48.dp)
                        }
                        save("$name-320dp-font${(scale * 100).toInt()}-consent")
                    }
                    "receive" -> {
                        compose.onNodeWithText(address).performScrollTo().assertIsDisplayed()
                        save("$name-320dp-font${(scale * 100).toInt()}-address")
                        assertFullyVisible(compose.activity.getString(jp.co.soramitsu.feature_wallet_impl.R.string.common_copy_address))
                        assertFullyVisible(compose.activity.getString(jp.co.soramitsu.feature_wallet_impl.R.string.ux_share_address))
                        save("$name-320dp-font${(scale * 100).toInt()}-actions")
                    }
                    "swap" -> {
                        compose.onNodeWithText("Minimum received").performScrollTo().assertIsDisplayed()
                        save("$name-320dp-font${(scale * 100).toInt()}-summary")
                        compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.feature_polkaswap_impl.R.string.ux_show_details)).performScrollTo().performClick()
                        compose.waitForIdle()
                        compose.onNodeWithText("XOR → VAL").performScrollTo().assertIsDisplayed()
                        save("$name-320dp-font${(scale * 100).toInt()}-details")
                    }
                }
            }
        }
    }

    private fun <T> callbacks(type: Class<T>): T = type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "No-op ${type.simpleName} fixture callbacks"
            else -> null
        }
    })!!

    private fun save(name: String) {
        val bitmap = compose.onNodeWithTag("fixture").captureToImage().asAndroidBitmap()
        val directory = compose.activity.getExternalFilesDir("ux-evidence")!!
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val arguments = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        if (arguments.getString("talkbackHoldFixture") == name) {
            // Optional device acceptance uses this real production view with synthetic data only.
            // Keep the installed TalkBack service active while the external keyboard traverses it.
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            val holdMillis = (arguments.getString("talkbackHoldSeconds")?.toLongOrNull() ?: 120L).coerceIn(1L, 180L) * 1000L
            val deadline = android.os.SystemClock.uptimeMillis() + holdMillis
            File(directory, "$name-hold-ready").writeText("ready")
            while (android.os.SystemClock.uptimeMillis() < deadline) {
                // TalkBack scroll animations also need the Compose test clock to advance.
                compose.mainClock.advanceTimeByFrame()
                Thread.sleep(16L)
            }
        }
    }

    private fun assertFullyVisible(text: String) {
        val node = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        val action = node.getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("fixture").getUnclippedBoundsInRoot()
        assertTrue("Complete $text action must fit within the actual viewport: $action / $viewport",
            action.left >= viewport.left && action.right <= viewport.right &&
                action.top >= viewport.top && action.bottom <= viewport.bottom)
    }
}
