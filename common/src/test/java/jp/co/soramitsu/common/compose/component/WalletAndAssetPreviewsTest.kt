package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The design previews of the wallet and asset components are private; they are rendered the way Android Studio's
 * preview does (reflection on the file class) to prove each one composes and shows its sample data.
 */
@RunWith(RobolectricTestRunner::class)
class WalletAndAssetPreviewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun pauseClock() {
        // Several previews contain loading shimmers, which animate forever.
        compose.mainClock.autoAdvance = false
    }

    private fun render(fileClass: String, preview: String) {
        val method = Class.forName("jp.co.soramitsu.common.compose.component.$fileClass")
            .getDeclaredMethod(preview, Composer::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        compose.setContent {
            FearlessAppTheme { Invoke(method) }
        }
        compose.mainClock.advanceTimeBy(500)
    }

    @Composable
    private fun Invoke(method: java.lang.reflect.Method) {
        method.invoke(null, currentComposer, 0)
    }

    private fun count(text: String) =
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun assetListItemPreviewShowsTwoRowsAndAShimmer() {
        render("AssetListItemKt", "PreviewAssetListItem")

        compose.onAllNodesWithText("KARURA ASSET · KARURA", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("444.30", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("AssetListItem_shimmer").assertCountEquals(1)
    }

    @Test
    fun walletItemPreviewShowsThreeWallets() {
        render("WalletItemKt", "WalletItemPreview")

        compose.onAllNodesWithText("My Wallet", useUnmergedTree = true).assertCountEquals(3)
        compose.onAllNodesWithText("$44400.3", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun walletNameItemPreviewShowsFourRowsAndASubtitle() {
        render("WalletNameItemKt", "WalletNameItemPreview")

        compose.onAllNodesWithText("My Wallet", useUnmergedTree = true).assertCountEquals(4)
        compose.onNodeWithText("Backed up from the browser extension", useUnmergedTree = true).assertExists()
    }

    @Test
    fun walletSelectorPreviewListsBothWallets() {
        render("WalletSelectorModalKt", "SelectWalletScreenPreview")

        compose.onNodeWithText(context.getString(R.string.common_title_select_wallet), useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("My Wallet", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun swipeBoxPreviewShowsTheAssetRow() {
        render("SwipeBoxKt", "AssetItemSwipeBoxPreview")

        compose.onAllNodesWithTag("SwipeBox").assertCountEquals(1)
        compose.onNodeWithText("KARURA ASSET · KARURA", useUnmergedTree = true).assertExists()
    }

    @Test
    fun smallComponentPreviewsShowTheirSamples() {
        render("HiddenAssetsItemKt", "PreviewHiddenAssetsItem")

        compose.onNodeWithText(context.getString(R.string.hidden_assets), useUnmergedTree = true).assertExists()
    }

    @Test
    fun assetBalancePreviewShowsTheAmount() {
        render("AssetBalanceKt", "PreviewAssetBalance")

        compose.onNodeWithText("44400.3").assertExists()
    }

    @Test
    fun chainsBadgePreviewCountsTheHiddenChains() {
        render("AssetChainsBadgeKt", "PreviewAssetChainBadge")

        compose.onNodeWithText("+3", useUnmergedTree = true).assertExists()
    }

    @Test
    fun nftStubPreviewShowsTheStub() {
        render("NftStubKt", "AddressPreview")

        compose.onNodeWithText(context.getString(R.string.nft_stub_title), useUnmergedTree = true).assertExists()
    }

    @Test
    fun assetSelectorPreviewShowsTheBadge() {
        render("AssetSelectorKt", "AssetSelectorPreview")

        compose.onNodeWithText("Kusama", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Pool", useUnmergedTree = true).assertExists()
    }

    @Test
    fun chainSelectorPreviewShowsBothSelectors() {
        render("ChainSelectorKt", "ChainSelectorPreview")

        compose.onAllNodesWithText("Kusama", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun selectorWithBorderPreviewShowsEveryVariant() {
        render("SelectorWithBorderKt", "SelectorWithBorderPreview")

        compose.onAllNodesWithText("Kusama", useUnmergedTree = true).assertCountEquals(7)
        assertTrue("subtitles are shown", count("300 KSM") + count("Kusama long string") >= 1)
    }
}
