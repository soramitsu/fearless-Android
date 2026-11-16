package jp.co.soramitsu.liquiditypools.impl.presentation.poollist

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.BasicPoolListItemState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PoolListScreenTest {
    @get:Rule val compose = createComposeRule()
    private val callbacks = mockk<PoolListScreenInterface>(relaxed = true)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pool = BasicPoolListItemState(StringPair("xor", "val"), "", "", "XOR-VAL", "$100", apy = null)

    @Test fun loadedRowsRoutePoolSelectionAndSearchText() {
        compose.setContent { FearlessAppTheme { PoolListScreen(PoolListState(listOf(pool), "", false), callbacks) } }
        compose.onNodeWithText("XOR-VAL").assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("xor")
        verify { callbacks.onPoolClicked(pool.ids); callbacks.onAssetSearchEntered("xor") }
    }

    @Test fun emptySearchDisplaysNoResultsMessage() {
        compose.setContent { FearlessAppTheme { PoolListScreen(PoolListState(searchQuery = "missing", isLoading = false), callbacks) } }
        compose.onNodeWithText(context.getString(R.string.common_search_network_and_assets_alert_description)).assertIsDisplayed()
        compose.onNodeWithText("missing").assertIsDisplayed()
    }

    @Test fun loadingDoesNotExposeStaleResultsOrEmptyMessage() {
        compose.setContent { FearlessAppTheme { PoolListScreen(PoolListState(pools = listOf(pool)), callbacks) } }
        compose.onNodeWithText("XOR-VAL").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.common_search_network_and_assets_alert_description)).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertExists()
    }
}
