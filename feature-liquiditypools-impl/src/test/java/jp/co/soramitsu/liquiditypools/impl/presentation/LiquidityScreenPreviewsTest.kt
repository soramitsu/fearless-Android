package jp.co.soramitsu.liquiditypools.impl.presentation

import android.content.Context
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_wallet_impl.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Each design-time preview renders its sample state, as the IDE and preview screenshots do. */
@RunWith(RobolectricTestRunner::class)
class LiquidityScreenPreviewsTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun showPreview(file: String, function: String) {
        val preview = Class.forName("jp.co.soramitsu.liquiditypools.impl.presentation.$file")
            .getDeclaredMethod(function, Composer::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        compose.setContent { FearlessAppTheme { preview.invoke(null, currentComposer, 0) } }
    }

    @Test
    fun addPreviewShowsItsYield() {
        showPreview("liquidityadd.LiquidityAddScreenKt", "PreviewLiquidityAddScreen")
        compose.onNodeWithText("23.3%").assertExists()
    }

    @Test
    fun addConfirmPreviewShowsBothAmounts() {
        showPreview("liquidityaddconfirm.LiquidityAddConfirmScreenKt", "PreviewLiquidityAddConfirmScreen")
        compose.onNodeWithText("100.003 XOR").assertExists()
        compose.onNodeWithText("12340443,24312 PSWAP").assertExists()
    }

    @Test
    fun removePreviewShowsTheWithdrawalWarning() {
        showPreview("liquidityremove.LiquidityRemoveScreenKt", "PreviewLiquidityRemoveScreen")
        compose.onNodeWithText(context.getString(R.string.lp_pool_remove_warning_text)).assertExists()
    }

    @Test
    fun removeConfirmPreviewShowsTheConfirmAction() {
        showPreview("liquidityremoveconfirm.LiquidityRemoveConfirmScreenKt", "PreviewLiquidityRemoveConfirmScreen")
        compose.onNodeWithText(context.getString(R.string.common_confirm)).assertExists()
    }

    @Test
    fun detailsPreviewShowsTvlAndYield() {
        showPreview("pooldetails.PoolDetailsScreenKt", "PreviewPoolDetailsScreen")
        compose.onNodeWithText("$34.999 TVL").assertExists()
        compose.onNodeWithText("23.3%").assertExists()
    }

    @Test
    fun poolListPreviewShowsTheEmptySearchHint() {
        showPreview("poollist.PoolListScreenKt", "PreviewPoolListScreen")
        compose.onNodeWithText(context.getString(R.string.common_search_network_and_assets_alert_description)).assertExists()
    }

    @Test
    fun allPoolsPreviewShowsSampleRows() {
        showPreview("allpools.AllPoolsScreenKt", "PreviewAllPoolsScreen")
        assertTrue(compose.onAllNodesWithText("XOR-VAL").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("TEXT1").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun poolItemPreviewShowsLoadedLoadingAndShimmerRows() {
        showPreview("allpools.BasicPoolListItemKt", "PreviewBasicPoolListItem")
        compose.onNodeWithText("XOR-VAL").assertExists()
        compose.onNodeWithText("Earn SWAP").assertExists()
        assertTrue(compose.onAllNodesWithText("text1").fetchSemanticsNodes().size == 3)
    }
}
