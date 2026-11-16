package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_liquiditypools_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AllPoolsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val selectedPools = mutableListOf<StringPair>()
    private val moreSelections = mutableListOf<Boolean>()
    private val windowHeights = mutableListOf<Dp>()
    private val headerHeights = mutableListOf<Dp>()
    private val callbacks = object : AllPoolsScreenInterface {
        override fun onPoolClicked(pair: StringPair) { selectedPools += pair }
        override fun onMoreClick(isUserPools: Boolean) { moreSelections += isUserPools }
        override fun onRefresh() = Unit
        override fun onWindowHeightChange(heightIs: Dp) { windowHeights += heightIs }
        override fun onHeaderHeightChange(heightIs: Dp) { headerHeights += heightIs }
    }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pool = BasicPoolListItemState(
        ids = StringPair("xor", "val"), token1Icon = "", token2Icon = "",
        text1 = "XOR-VAL", text2 = "$1,234", apy = LoadingState.Loaded("12.5%"),
        text4 = TextModel.SimpleString("Earn PSWAP")
    )

    @Test
    fun fundedAndAvailablePoolsShowTheirAmountsAndRouteSelection() {
        compose.setContent {
            FearlessAppTheme {
                AllPoolsScreen(
                    AllPoolsState(userPools = listOf(pool), allPools = listOf(pool.copy(text1 = "XOR-DAI")), isLoading = false),
                    callbacks
                )
            }
        }
        compose.onNodeWithText("XOR-VAL").assertIsDisplayed().performClick()
        compose.onNodeWithText("XOR-DAI").assertIsDisplayed()
        compose.onAllNodesWithText("$1,234")[0].assertIsDisplayed()
        assertEquals(listOf(pool.ids), selectedPools)
        assertTrue(windowHeights.any { it.value > 0 })
        assertTrue(headerHeights.any { it.value > 0 })
    }

    @Test
    fun moreButtonsDistinguishOwnedAndAvailablePools() {
        compose.setContent {
            FearlessAppTheme {
                AllPoolsScreen(
                    AllPoolsState(listOf(pool), listOf(pool.copy(text1 = "XOR-DAI")), true, true, false), callbacks
                )
            }
        }
        val more = context.getString(R.string.common_more).uppercase()
        compose.onAllNodesWithText(more)[0].performClick()
        compose.onAllNodesWithText(more)[1].performClick()
        assertEquals(listOf(true, false), moreSelections)
    }

    @Test
    fun loadingHidesStalePoolRowsAndMoreActions() {
        compose.setContent {
            FearlessAppTheme { AllPoolsScreen(AllPoolsState(userPools = listOf(pool), isLoading = true), callbacks) }
        }
        compose.onNodeWithText("XOR-VAL").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.common_more).uppercase()).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.lp_available_pools_title)).assertIsDisplayed()
    }

    @Test
    fun aPoolWithoutYieldOrRewardsStillShowsItsBalanceAndSelection() {
        compose.setContent {
            FearlessAppTheme {
                BasicPoolListItem(state = pool.copy(apy = null, text4 = null), onPoolClick = callbacks::onPoolClicked)
            }
        }
        compose.onNodeWithText("XOR-VAL").performClick()
        compose.onNodeWithText("$1,234").assertIsDisplayed()
        compose.onNodeWithText("Earn PSWAP").assertDoesNotExist()
        assertEquals(listOf(pool.ids), selectedPools)
    }

    @Test
    fun loadingYieldRetainsThePoolIdentityAndRewardLabel() {
        compose.setContent {
            FearlessAppTheme { BasicPoolListItem(state = pool.copy(apy = LoadingState.Loading())) }
        }
        compose.onNodeWithText("XOR-VAL").assertIsDisplayed()
        compose.onNodeWithText("Earn PSWAP").assertIsDisplayed()
        compose.onNodeWithText("12.5%", substring = true).assertDoesNotExist()
    }
}
