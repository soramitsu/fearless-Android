package jp.co.soramitsu.polkaswap.impl.presentation.select_market

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class SelectMarketContentTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val selected = mutableListOf<Market>()

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    @Test fun loadingShowsOnlyTheTitle() {
        compose.setContent { FearlessAppTheme { SelectMarketContent(LoadingState.Loading()) { selected += it } } }

        compose.onNodeWithText(context.getString(R.string.polkaswap_market_algorithm_title)).assertIsDisplayed()
        Market.entries.forEach { compose.onNodeWithText(it.marketName).assertDoesNotExist() }
    }

    @Test fun loadedMarketsAreSelectableAndExplainThemselves() {
        compose.setContent {
            FearlessAppTheme { SelectMarketContent(LoadingState.Loaded(listOf(Market.SMART, Market.TBC))) { selected += it } }
        }

        compose.onNodeWithText(Market.XYK.marketName).assertDoesNotExist()
        compose.onNodeWithText(Market.TBC.marketName).performClick()
        assertEquals(listOf(Market.TBC), selected)
        compose.onNodeWithText(context.getString(Market.TBC.descriptionId)).assertDoesNotExist()

        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasClickAction()))[0].performClick()
        compose.onNodeWithText(context.getString(Market.SMART.descriptionId)).assertIsDisplayed()
        assertEquals(listOf(Market.TBC), selected)
    }

    @Test fun previewFixtureShowsTheLoadingSheet() {
        compose.setContent { SelectMarketContentPreview() }

        compose.onNodeWithText(context.getString(R.string.polkaswap_market_algorithm_title)).assertIsDisplayed()
    }
}
