package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class ActionBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun actionBarLabelsEachActionAndReportsItWithTheAsset() {
        val clicks = mutableListOf<Triple<ActionItemType, String, String>>()
        compose.show(still) {
            Column {
                ActionBar(
                    state = ActionBarViewState(
                        chainId = "polkadot",
                        chainAssetId = "dot",
                        actionItems = listOf(ActionItemType.SEND, ActionItemType.RECEIVE, ActionItemType.BUY),
                        disabledItems = listOf(ActionItemType.BUY)
                    ),
                    fillMaxWidth = true,
                    onItemClick = { type, chain, asset -> clicks += Triple(type, chain, asset) }
                )
                ActionBar(
                    state = ActionBarViewState("kusama", "ksm", listOf(ActionItemType.SWAP, ActionItemType.CROSS_CHAIN))
                )
            }
        }

        compose.onNodeWithText(context.getString(ActionItemType.BUY.titleId)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(ActionItemType.SWAP.titleId)).assertIsEnabled()
        compose.onNodeWithText(context.getString(ActionItemType.CROSS_CHAIN.titleId)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(ActionItemType.SEND.titleId)).tap()
        compose.onNodeWithText(context.getString(ActionItemType.RECEIVE.titleId)).tap()
        compose.onNodeWithText(context.getString(ActionItemType.BUY.titleId)).tap()

        assertEquals(
            listOf(Triple(ActionItemType.SEND, "polkadot", "dot"), Triple(ActionItemType.RECEIVE, "polkadot", "dot")),
            clicks
        )
    }

    @Test
    fun actionItemTypesPairIconsWithTitles() {
        assertEquals(7, ActionItemType.entries.size)
        assertEquals(R.string.common_action_send, ActionItemType.SEND.titleId)
        assertEquals(R.drawable.ic_common_send, ActionItemType.SEND.iconId)
        assertEquals(ActionItemType.HIDE.iconId, ActionItemType.SHOW.iconId)
        assertEquals(R.string.common_action_swap, ActionItemType.SWAP.titleId)
    }

    @Test
    fun actionCellIsClickableOnlyWhenEnabled() {
        var clicks = 0
        compose.show(still) {
            Column {
                ActionCell(
                    state = ActionCellViewState(painter = painterResource(R.drawable.ic_common_send), title = "Send"),
                    onClick = { clicks++ }
                )
                ActionCell(
                    state = ActionCellViewState(painter = painterResource(R.drawable.ic_common_send), title = "Locked", isEnabled = false),
                    onClick = { clicks++ }
                )
            }
        }

        compose.onNodeWithTag("ActionCell_Send").assertWidthIsEqualTo(80.dp).assertHasClickAction().tap()
        compose.onNodeWithTag("ActionCell_Locked").assertHasNoClickAction()
        compose.onNodeWithText("Locked", useUnmergedTree = true).assertIsDisplayed()

        assertEquals(1, clicks)
    }

    @Test
    fun stillShimmersKeepTheirPlaceholderSizes() {
        compose.show(still) { shimmers() }

        assertShimmerSizes()
    }

    @Test
    fun movingShimmersSweepWithoutChangingTheirSizes() {
        compose.mainClock.autoAdvance = false
        compose.show(FwEffects(FwTier.Full, highContrast = false, reduceMotion = false)) { shimmers() }
        compose.mainClock.advanceTimeBy(400)

        assertShimmerSizes()
    }

    @Composable
    private fun shimmers() {
        Column {
            ShimmerB0(Modifier.width(100.dp).testTag("b0"))
            ShimmerB1(Modifier.width(100.dp).testTag("b1"))
            ShimmerB2(Modifier.width(100.dp).testTag("b2"))
            Shimmer(Modifier.size(20.dp).testTag("plain"))
            ShimmerRectangle(Modifier.size(64.dp).testTag("rect"))
            RoundedRect(Modifier.size(30.dp).testTag("rounded"))
            Column(Modifier.testTag("bar")) { ActionBarShimmer(items = 3, fillMaxWidth = true) }
            Column(Modifier.testTag("compact-bar")) { ActionBarShimmer() }
        }
    }

    private fun assertShimmerSizes() {
        compose.onNodeWithTag("b0").assertHeightIsEqualTo(17.dp)
        compose.onNodeWithTag("b1").assertHeightIsEqualTo(15.dp)
        compose.onNodeWithTag("b2").assertHeightIsEqualTo(13.dp)
        compose.onNodeWithTag("plain").assertWidthIsEqualTo(20.dp)
        compose.onNodeWithTag("rect").assertHeightIsEqualTo(64.dp)
        compose.onNodeWithTag("rounded").assertWidthIsEqualTo(30.dp)
        // 64 dp rectangles with 8 dp vertical padding.
        compose.onNodeWithTag("bar").assertHeightIsEqualTo(80.dp)
        compose.onNodeWithTag("compact-bar").assertHeightIsEqualTo(80.dp)
    }

    private fun SemanticsNodeInteraction.tap() {
        performClick()
        compose.waitForIdle()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    private fun ComposeContentTestRule.show(effects: FwEffects, content: @Composable () -> Unit) = setContent {
        FearlessAppTheme {
            CompositionLocalProvider(LocalFwEffects provides effects, content = content)
        }
    }

    private companion object {
        val still = FwEffects(FwTier.Full, highContrast = false, reduceMotion = true)
    }
}
