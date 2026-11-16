package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class HiddenAssetsItemTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun collapsedAndExpandedRowsShowTheLabelAndReportTaps() {
        var collapsedTaps = 0
        var expandedTaps = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    Column(Modifier.testTag("collapsed")) {
                        HiddenAssetsItem(state = HiddenItemState(), onClick = { collapsedTaps++ })
                    }
                    Column(Modifier.testTag("expanded")) {
                        HiddenAssetsItem(state = HiddenItemState(isExpanded = true), onClick = { expandedTaps++ })
                    }
                }
            }
        }

        val label = context.getString(R.string.hidden_assets)
        compose.onAllNodesWithText(label).assertCountEquals(2)
        compose.onNodeWithTag("collapsed").assertIsDisplayed()
        compose.onAllNodesWithText(label)[1].performClick()
        compose.waitForIdle()

        assertEquals(0, collapsedTaps)
        assertEquals(1, expandedTaps)

        compose.onAllNodesWithText(label)[0].performClick()
        compose.waitForIdle()
        assertEquals(1, collapsedTaps)
    }
}
